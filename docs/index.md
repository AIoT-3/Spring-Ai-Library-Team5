# 공공 도서 데이터 기반 AI 검색 시스템

## 0. 프로젝트 개요

### 프로젝트명
`ai-library-platform`

### 목적
공공 도서 데이터를 활용하여 **전통적인 검색 시스템이 AI 시스템(RAG · Vector · MCP)으로 진화하는 전체 흐름**을 학습합니다.

이 프로젝트는 단순히 AI 기능을 붙이는 것이 아니라,
* **왜** AI가 필요한지
* AI를 기존 백엔드 아키텍처에 **어떻게** 녹이는지
* AI 도입 시 발생하는 **성능·비용·운영** 문제를 어떻게 다루는지
를 단계적으로 경험하는 것을 목표로 합니다.

> **핵심 메시지**
> AI는 처음부터 등장하지 않습니다. **데이터 → 검색 → 의미 → 생성 → 판단 → 도구 활용**으로 진화합니다.

### 학생들을 위한 학습 가이드
본 문서는 단순한 소스코드 제공이 아닌, 각 단계별로 **"무엇을(What)", "어떻게(How)", "왜(Why)"** 구현해야 하는지를 안내합니다.
코드를 직접 작성하기 전에 해당 스텝의 가이드 문서를 충분히 읽고, 제시된 **학습 포인트**와 **참고 링크**를 통해 기술적 배경을 먼저 이해하는 것을 권장합니다.

---

## 1. 프로젝트 패키지 구조

```
src/main/java/com/nhnacademy/library
 ├─ batch.init     # 공공 데이터 적재 및 초기화 로직
 ├─ core.book      # 도서 관련 핵심 비즈니스 로직 및 DB 접근
 ├─ core.config    # 공통 설정 (QueryDSL 등)
 ├─ front.web      # 웹 컨트롤러 및 화면 처리
 ├─ telegram       # Telegram Bot UI
 └─ external       # 외부 API 연동
```

### 각 패키지 역할

* **com.nhnacademy.library.core.book**

    * 도서 조회 및 상세 정보 처리
    * 검색 엔진: Keyword, Vector, Hybrid, RAG (Strategy 패턴 적용)
    * 벡터 유사도 기반 의미적 캐싱 (Semantic Caching)
    * DB 중심 비즈니스 로직(Business Logic)

* **com.nhnacademy.library.core.review**

    * 도서 리뷰 도메인 및 리포지토리
    * AI 기반 리뷰 요약 (Map-Reduce 전략)

* **com.nhnacademy.library.batch.init**

    * 공공 CSV 데이터 파싱(Parsing) 및 적재(Load)
    * Google Books API 데이터 보강
    * 임베딩(Embedding) 생성

* **com.nhnacademy.library.front.web**

    * 도서 검색 및 상세 페이지 UI 컨트롤러
    * AI 질의 UI

* **com.nhnacademy.library.telegram**

    * Telegram Bot 기반 검색 UI
    * 대화 관리 및 피드백 수집

> 외부 Open API는 **Batch 또는 MCP를 통해서만 접근**하며,
> 서비스 런타임 검색에서는 직접 호출하지 않는다.

---

## 2. 기술 스택

* Java 21
* Spring Boot 4
* Spring Data JPA (기본 CRUD)
* QueryDSL (동적 쿼리)
* PostgreSQL
* pgvector
* LLM (API 또는 로컬)
* Telegram Bot API

---

## 3. 전체 시스템 흐름 요약

1. CSV 기반 데이터 적재
2. 외부 Open API를 통한 데이터 보강 (캐싱)
3. 키워드 기반 검색 구현
4. 검색 품질 및 성능 한계 체감
5. Vector 검색 도입
6. RAG 기반 AI 응답 생성
7. AI 성능·비용 튜닝
8. **Telegram Bot 기반 UI 구축**
9. MCP 기반 AI 도구 활용

---

## 4. 단계별 시나리오

### Step 1 — 데이터 구축과 전통적 검색

#### 주요 흐름

* [01. 데이터 적재: 공공 도서 CSV Batch Load](step-1/01.csv-batch-load.md)
* [02. 기본 검색: LIKE 기반 동적 쿼리 구현](step-1/02.basic-search-implementation.md)
* [03. 검색 API: REST API 엔드포인트 구현](step-1/03.search-api.md)
* [04. 성능 최적화: 인덱싱과 전문 검색(Full Text Search)](step-1/04.search-optimization.md)

#### 학습 포인트

* 데이터 구축(Corpus)과 이벤트 기반 아키텍처(EDA) 이해
* QueryDSL을 활용한 타입 세이프(Type-safe) 동적 쿼리
* RDBMS 검색의 성능 한계와 인덱싱(B-Tree, GIN) 전략

---

### Step 2 — 검색의 진화: 벡터 검색(Vector Search)

#### 주요 흐름

* [01. 한계 체감: 키워드 검색의 품질 문제 분석](step-2/01.search-quality-limitations.md)
* [02. 환경 구축: PostgreSQL pgvector 설정](step-2/02.pgvector-setup.md)
* [03. 지식 수치화: AI 임베딩(Embedding) 생성 Batch](step-2/03.embedding-generation.md)
* [04. 의미 검색: 자연어 기반 벡터 검색 도입](step-2/04.natural-language-search.md)
* [05. 하이브리드 검색: 키워드와 벡터의 결합](step-2/05.hybrid-search.md)

#### 학습 포인트

* **의미 기반 검색(Semantic Search)**: 키워드 매칭의 한계를 이해하고 벡터 공간에서의 유사도 검색 원리를 습득합니다.
* **벡터 데이터베이스 활용**: PostgreSQL `pgvector`를 통해 비정형 데이터(텍스트)를 수치화하여 저장하고 검색하는 실무 기법을 익힙니다.
* **AI 모델 통합**: 외부 임베딩 모델 API를 백엔드 서비스와 연동하고 대량 데이터를 처리하는 배치 프로세스를 경험합니다.

---

### Step 3 — RAG: AI가 답변을 생성하다

#### 주요 흐름

* [01. RAG 이해: Retrieval-Augmented Generation 개념과 구조](step-3/01.understanding-rag.md)
* [02. Context 구성: Vector 검색 결과 기반 프롬프트 문맥화](step-3/02.context-construction.md)
* [03. LLM 연동: 추천/요약/설명 생성](step-3/03.llm-integration.md)

#### 학습 포인트

* Hallucination 문제
* RAG의 필요성
* Context 품질의 중요성

---

### Step 4 — AI 성능 튜닝

#### 주요 흐름

* [01. 성능과 비용: 응답 지연 및 비용 문제 인식](step-4/01.performance-cost.md)
* [02. 최적화: Top-K 및 Context 길이 제한](step-4/02.topk-optimization.md)
* [03. 캐싱: 결과 캐싱(Semantic Caching)](step-4/03.semantic-caching.md)
* [04. 요약 전략과 리뷰 요약 실습](step-4/04.review-summarization.md)

#### 학습 포인트

* AI 성능 문제는 설계 문제
* 비용·속도·품질의 균형

---

### Step 5 — Telegram Bot: 대화형 검색 UI

#### 주요 흐름

* [01. Telegram Bot 설정](step-5/01.telegram-bot-setup.md)
* [02. 하이브리드 검색 연동](step-5/02.hybrid-search-via-telegram.md)
* [03. 대화 관리](step-5/03.conversation-management.md)
* [04. 사용자 피드백](step-5/04.user-feedback.md)

#### 학습 포인트

* **대화형 UI**: 텍스트 기반 자연스러운 검색 경험
* **컨텍스트 유지**: 이전 검색 기록 기반 후속 질문 처리
* **사용자 피드백 루프**: 검색 품질 개선을 위한 피드백 수집

> Step 5는 Step 1~4에서 구축한 하이브리드 검색 시스템을
> Telegram Bot이라는 실용적인 UI로 제공하여
> 사용자 경험을 극대화합니다.

---

### Step 6 — 개인화: 사용자 맞춤형 추천

#### 주요 흐름

* [01. 벡터 임베딩 기초: 임베딩의 이해와 필요성](step-6/01.vector-embedding-basics.md)
* [02. 사용자 선호 벡터: 피드백 기반 선호도 학습](step-6/02.user-preference-vector.md)
* [03. 개인화 랭킹: 사용자 맞춤형 도서 추천](step-6/03.personalized-ranking.md)
* [04. 콜드 스타트와 최적화: 신규 사용자 및 성능 튜닝](step-6/04.cold-start-and-optimization.md)
* [05. 통합 가이드: 개인화 시스템 실전 구현](step-6/05.integration-guide.md)

#### 학습 포인트

* 사용자 피드백 루프 구축
* 벡터 공간에서의 사용자 프로필링
* 콜드 스타트 문제 해결
* 개인화와 다양성의 균형

---

### Step 7 — LLM 기반 오케스트레이션: 지능형 도서관 도우미

#### 주요 흐름

* [00. 이론: MCP, A2A, Agent 개념](step-7/00.theory.md)
* [01. 개요 및 시나리오: LLM 오케스트레이션 소개](step-7/01.overview.md)
* [02. 단일 Function: 도서 검색 Function 구현](step-7/02.single-function.md)
* [03. 복수 Function: 여러 Function 조합 실행](step-7/03.multiple-functions.md)
* [04. 외부 API 연동: 도서관정보나루 API 연동](step-7/04.external-api.md)
* [05. @Tool 애노테이션: Spring AI 1.1.2 새로운 방식](step-7/04.tool-annotation.md)
* [06. 실습 가이드: 단계별 실습 미션](step-7/05.practice-guide.md)

#### 학습 포인트

* **Function Calling**: LLM이 스스로 적절한 Function 선택
* **@Tool 애노테이션**: Spring AI 1.1.2의 간결한 Function 정의 방식
* **A2A 패턴**: Agent-to-Agent 통신을 통한 복잡한 작업 처리
* **오케스트레이션**: 여러 Function 조합과 결과 종합
* **17개 도서관정보나루 API → 8개 주요 Function** 변환

#### 구현된 컴포넌트

**LibraryTools** (@Tool 기반, 8개 메서드):
- `searchBooks` - 내부 DB → 도서관정보나루 Fallback
- `getLibrariesWithBook` - 대출 가능 도서관 조회
- `searchLibraries` - 도서관 검색
- `getBookDetail` - 도서 상세 정보
- `getPopularBooks` - 인기대출도서 조회
- `getHotTrendBooks` - 대출 급상승 도서 조회
- `getRecommendedBooks` - 추천도서 조회
- `checkBookExists` - 도서 소장 여부 확인

**InternalBookTools** (@Tool 기반, 3개 메서드):
- `searchInternalBooks` - 내부 DB 하이브리드 검색
- `getReviews` - 리뷰 조회
- `checkLoanAvailability` - 대출 가능 여부 조회

**ToolBasedAiOrchestrationService**:
- @Tool 기반 오케스트레이션 서비스
- FunctionToolCallback.builder() → @Tool 애노테이션
- `.defaultTools()`로 자동 등록

---

## 5. 활용 예시 요약

* Vector 검색 MCP
* 외부 도서 정보 보강 MCP
* 신규 도서 등록 MCP
* 임베딩 Batch 트리거 MCP
* 검색 전략 선택 MCP

---

## 6. 이 프로젝트로 가르칠 수 있는 것

* AI 백엔드 시스템 설계 사고방식
* RAG 실전 적용 구조
* Vector 검색과 임베딩 이해
* AI 성능·비용 튜닝 포인트
* MCP 기반 Tool 사용 패턴
* Spring 기반 서비스에 AI 통합
* **대화형 UI 구현 능력**

---

## 7. 최종 요약 문장

> 이 프로젝트는 공공 데이터를 출발점으로,
> 검색 시스템이 어떻게 AI 시스템으로 진화하는지를
> **데이터 → 의미 → 생성 → 판단 → 도구 활용**의 흐름으로 경험하는 실습형 AI 백엔드 시나리오입니다.
>
> **Step 5**에서는 이 모든 기능을 Telegram Bot이라는 실용적인 UI로 제공하여,
> 사용자가 자연스럽게 AI 검색 시스템을 활용할 수 있도록 합니다.
