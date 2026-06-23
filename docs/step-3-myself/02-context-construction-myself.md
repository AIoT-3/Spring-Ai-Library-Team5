# 02. 현재 프로젝트 RAG 컨텍스트 구성

이 문서는 강의자료 `docs/step-3/02.context-construction.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 예시는 record 스타일 getter나 `volumeTitle` 같은 필드가 섞일 수 있다. 현재 프로젝트의 `BookSearchResponse`는 Lombok `@Getter` 기반 클래스이며, 분류 필드는 `category`다.

## 컨텍스트의 목적

컨텍스트는 LLM에게 전달하는 근거 자료다. 검색 결과 객체를 그대로 넘길 수 없으므로, 필요한 필드만 짧고 일관된 텍스트로 변환한다.

```text
검색 결과 BookSearchResponse 목록
  -> RagContextBuilder
      -> 도서 ID, 제목, 저자, 출판사, category, 출간일, 소개글, 점수
          -> 프롬프트에 삽입
```

## 포함할 필드

| 필드 | 포함 여부 | 이유 |
| --- | --- | --- |
| `id` | 필수 | LLM 응답과 실제 도서 매칭 |
| `title` | 필수 | 추천 대상 |
| `authorName` | 권장 | 저자 근거 |
| `publisherName` | 선택 | 보조 정보 |
| `category` | 권장 | 분류 정보, `volumeTitle` 대체 |
| `editionPublishDate` | 권장 | 최신성 판단 |
| `bookContent` | 필수에 가까움 | 추천 이유 생성 근거 |
| `isbn` | 선택 | 식별 정보 |
| `similarity`, `rrfScore` | 개발/디버깅용 | LLM 판단 근거로는 과신 금지 |

가격은 추천 이유에 큰 영향을 주지 않는다면 제외한다. 토큰을 아끼는 편이 낫다.

## 컨텍스트 예시

```text
## 참고 도서

### 도서 1
- ID: 101
- 제목: 자바의 정석
- 저자: 남궁성
- 출판사: 도우출판
- 분류: 005.13
- 출간일: 2021-01-01
- 소개: 자바 입문자와 중급자를 위한 문법과 객체지향 설명...

### 도서 2
- ID: 205
- 제목: 이것이 자바다
- 저자: 신용권
- 출판사: 한빛미디어
- 분류: 005.13
- 출간일: 2020-09-15
- 소개: 예제 중심으로 자바 기본기를 다루는 입문서...
```

`분류`에는 현재 프로젝트의 `category` 값을 넣는다. 강의자료의 권명 예시를 따라 `volumeTitle`을 추가하지 않는다.

## RagContextBuilder 책임

컨텍스트 생성은 Service 내부 private method로 시작해도 되지만, Step 4에서 Top-K와 리뷰 요약까지 붙으면 금방 커진다. 별도 클래스로 두는 편이 좋다.

```java
@Component
public class RagContextBuilder {

    private static final int MAX_CONTENT_LENGTH = 300;

    public String build(List<BookSearchResponse> books) {
        StringBuilder context = new StringBuilder("## 참고 도서\n\n");

        for (int i = 0; i < books.size(); i++) {
            BookSearchResponse book = books.get(i);
            context.append("### 도서 ").append(i + 1).append('\n');
            context.append("- ID: ").append(book.getId()).append('\n');
            context.append("- 제목: ").append(nullToDash(book.getTitle())).append('\n');
            context.append("- 저자: ").append(nullToDash(book.getAuthorName())).append('\n');
            context.append("- 출판사: ").append(nullToDash(book.getPublisherName())).append('\n');
            context.append("- 분류: ").append(nullToDash(book.getCategory())).append('\n');
            context.append("- 출간일: ").append(book.getEditionPublishDate() == null ? "-" : book.getEditionPublishDate()).append('\n');
            context.append("- 소개: ").append(truncate(book.getBookContent(), MAX_CONTENT_LENGTH)).append("\n\n");
        }

        return context.toString();
    }
}
```

실제 구현에서는 `nullToDash`, `truncate` 같은 작은 유틸리티를 같은 클래스 private method로 두면 충분하다.

## 토큰 제한

처음 구현은 아래 기준을 추천한다.

| 항목 | 권장값 |
| --- | --- |
| RAG 후보 도서 수 | 5권 |
| 도서 소개글 길이 | 300자 이하 |
| 리뷰 요약 포함 전 | 도서 기본 정보만 |
| 리뷰 요약 포함 후 | 요약 100자 이하 |

Step 3에서는 리뷰를 아직 넣지 않는다. Step 4에서 리뷰 요약을 붙일 때 컨텍스트 길이를 다시 조정한다.

## 프롬프트 규칙

컨텍스트만 잘 만들어도 LLM이 검색 결과 밖의 책을 지어낼 수 있다. 프롬프트에 아래 제약을 반드시 넣는다.

```text
1. 반드시 참고 도서 목록에 있는 책만 추천한다.
2. 응답의 id는 참고 도서의 ID 중 하나여야 한다.
3. 참고 도서에 근거가 없으면 추천하지 않는다.
4. JSON 외의 문장을 출력하지 않는다.
```

## 완료 기준

```text
1. 컨텍스트에 id, title, authorName, category, bookContent가 포함된다.
2. volumeTitle은 사용하지 않는다.
3. bookContent는 길이를 제한한다.
4. 컨텍스트 생성 로직은 LLM 호출 로직과 분리된다.
5. 빈 검색 결과일 때 LLM을 호출하지 않는다.
```
