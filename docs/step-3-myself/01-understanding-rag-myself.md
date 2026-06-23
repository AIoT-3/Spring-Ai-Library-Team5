# 01. 현재 프로젝트에서 RAG 책임 정리하기

이 문서는 강의자료 `docs/step-3/01.understanding-rag.md`를 현재 프로젝트 기준으로 보강한 내용이다.

원본 문서는 RAG 개념을 설명한다. 현재 프로젝트에서는 이미 `SearchType.RAG`가 있고, 화면에도 RAG 선택지가 있으므로 “RAG가 기존 검색 타입들과 어떻게 연결되는지”를 먼저 정해야 한다.

## 현재 전제

Step 2까지의 검색 타입은 아래처럼 역할을 나눈다.

| SearchType | 역할 |
| --- | --- |
| `KEYWORD` | 기존 QueryDSL LIKE 검색 |
| `VECTOR` | 검색어 임베딩과 `books.embedding vector(1024)` 거리 검색 |
| `HYBRID` | 키워드 검색과 벡터 검색을 RRF로 병합 |
| `RAG` | `HYBRID` 결과를 근거로 LLM 추천 이유 생성 |

`RAG`는 DB 검색 방식이 아니라 애플리케이션 서비스 흐름이다. Repository에 `ragSearch`를 만들기보다 Service에서 아래 흐름을 조율하는 편이 좋다.

```text
BookSearchController
  -> BookSearchService or BookRagService
      -> HYBRID 검색으로 후보 도서 조회
      -> RagContextBuilder
      -> BookAiService(ChatModel)
      -> JSON 응답 파싱
      -> 화면 모델 구성
```

## RAG가 해결하는 문제

검색 결과 목록만 보여주면 사용자는 왜 그 책이 추천됐는지 직접 판단해야 한다. RAG는 검색된 도서 정보를 근거로 추천 이유를 생성한다.

```text
사용자 질문:
  "자바 처음 배우는 사람이 볼 책 추천해줘"

HYBRID 검색:
  관련 도서 후보 10권

RAG:
  후보 도서의 제목, 저자, category, 소개글을 근거로
  "왜 이 책이 적합한지" 설명
```

LLM이 새 책을 지어내면 안 된다. 반드시 검색 결과 안에 있는 도서 ID만 추천하게 해야 한다.

## 현재 프로젝트 필드 기준

강의자료 예시에 `volumeTitle`이 나오더라도 현재 프로젝트에는 없다. RAG 컨텍스트와 JSON 응답은 아래 필드를 기준으로 만든다.

| 필드 | 사용 목적 |
| --- | --- |
| `id` | LLM 응답을 실제 도서와 매칭하는 키 |
| `isbn` | 정확한 식별 보조 정보 |
| `title` | 추천 표시 핵심 |
| `authorName` | 저자 근거 |
| `publisherName` | 출판사 정보 |
| `editionPublishDate` | 최신성 판단 |
| `category` | 분류 정보, `volumeTitle` 대체 |
| `bookContent` | 의미 판단 근거 |
| `similarity` | 벡터 검색 점수 |
| `rrfScore` | 하이브리드 최종 점수 |

## 추천 패키지 구조

현재 구조에 맞춰 RAG 관련 코드는 검색 Repository와 분리한다.

```text
com.nhnacademy.ailibrarymyself.core.book.rag
  BookRagService.java
  RagContextBuilder.java
  RagPromptTemplate.java
  RagResponseParser.java

com.nhnacademy.ailibrarymyself.core.book.ai
  BookAiService.java

com.nhnacademy.ailibrarymyself.core.book.dto
  BookAiRecommendationResponse.java
  BookRagResult.java
```

처음부터 많은 추상화를 넣을 필요는 없다. 다만 `ContextBuilder`, `PromptTemplate`, `AiService`는 분리하는 편이 테스트와 디버깅이 쉽다.

## RAG에서 하지 말아야 할 것

| 하지 말 것 | 이유 |
| --- | --- |
| LLM에게 DB 전체를 설명하게 하기 | 토큰과 비용이 폭발함 |
| 검색 없이 LLM에게 추천만 요청하기 | 실제 보유 도서와 맞지 않을 수 있음 |
| LLM 응답의 title만 믿고 매칭하기 | 제목 중복/변형 가능 |
| `volumeTitle` 필드를 새로 되살리기 | 현재 엔티티 기준과 어긋남 |
| RAG 실패 시 전체 요청 실패 | 검색 결과 fallback이 가능해야 함 |

## 완료 기준

```text
1. RAG는 HYBRID 검색 결과를 입력으로 사용한다.
2. LLM은 검색 결과에 포함된 id만 추천한다.
3. 컨텍스트는 현재 BookSearchResponse 필드 기준으로 만든다.
4. category를 사용하고 volumeTitle은 사용하지 않는다.
5. LLM 실패 시 검색 결과 fallback 정책이 있다.
```
