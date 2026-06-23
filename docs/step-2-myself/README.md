# Step 2 Myself

이 패키지는 `docs/step-2` 강의자료를 현재 프로젝트 기준으로 보강한 문서 모음이다.

원본 Step 2는 벡터 검색의 개념과 큰 구현 흐름을 설명한다. 이 보강본은 실제로 구현할 때 빠지기 쉬운 부분을 채운다.

- 현재 프로젝트 패키지명과 기존 클래스 기준으로 적용한다.
- Spring Boot `3.5.10`, Spring AI `1.1.4`, PostgreSQL, pgvector 조합을 전제로 한다.
- H2 단위 테스트와 PostgreSQL 전용 기능 검증을 분리한다.
- 처음부터 RAG까지 섞지 않고 `KEYWORD -> VECTOR -> HYBRID` 순서로 확장한다.

## 챕터 구성

| 원본 | 보강 문서 | 핵심 보강 |
| --- | --- | --- |
| `01.search-quality-limitations.md` | `01-search-quality-limitations-myself.md` | 검색 품질을 실제 프로젝트에서 어떻게 비교하고 기록할지 |
| `02.pgvector-setup.md` | `02-pgvector-setup-myself.md` | pgvector DDL, 차원 고정, H2 테스트 분리 |
| `03.embedding-generation.md` | `03-embedding-generation-myself.md` | 임베딩 대상 텍스트, 배치 재시작, 실패 처리 |
| `04.natural-language-search.md` | `04-natural-language-search-myself.md` | vector search 쿼리 설계, 점수 DTO, count 쿼리 주의 |
| `05.hybrid-search.md` | `05-hybrid-search-myself.md` | RRF 병합, 페이징 한계, Service 구조 |

## 구현 권장 순서

```text
1. 현재 키워드 검색 기준선 만들기
2. PostgreSQL에 pgvector extension과 vector 컬럼 확인
3. 임베딩 생성/저장 배치 만들기
4. VECTOR 검색을 별도 repository 메서드로 붙이기
5. KEYWORD + VECTOR 결과를 HYBRID에서 RRF로 병합하기
6. PostgreSQL 기반 통합 테스트 또는 수동 검증 쿼리 추가하기
```

Step 2에서 중요한 것은 기능을 한 번에 많이 넣는 것이 아니라, 검색 결과가 왜 좋아졌는지 또는 왜 나빠졌는지 비교할 수 있는 기준을 남기는 것이다.

---

## 코드 리뷰 의견

### 총평

문서의 구성과 보강 방향이 매우 좋다. 강의자료에서 `volumeTitle`을 제외하고 `category`로 대체한 결정이 일관되게 반영되어 있고, 챕터별로 완료 기준이 명확하다. 다만 현재 Step 1 구현 코드와 이 문서를 같이 보면 구현 진입 시 놓치기 쉬운 포인트가 몇 가지 있다.

### 1. `BookSearchService`에 SearchType 분기 뼈대를 먼저 잡아야 한다

현재 `BookSearchService`는 모든 요청을 `bookRepository.search(...)`로만 보낸다. Step 2 문서에서는 `KEYWORD → VECTOR → HYBRID` 분기를 명확히 설명하지만, 실제 분기를 만들 때 Service 구조를 어떻게 잡을지 두 가지 선택지가 있다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| `BookSearchService` 내부 `switch` 분기 | 코드가 한 곳에 모여 흐름 파악이 쉬움 | HYBRID, RAG까지 붙으면 메서드가 길어짐 |
| Strategy 패턴 (`SearchStrategy` 인터페이스) | 검색 타입별 단위 테스트 분리 가능 | Step 2에서는 과도한 추상화일 수 있음 |

**추천**: Step 2에서는 Service 내부 switch로 시작하되, `hybridSearch()`를 별도 private 메서드로 분리한다. Step 3에서 RAG까지 붙이는 시점에 Strategy로 리팩토링해도 늦지 않다. 단, switch 분기 자체는 Step 2 첫 커밋에서 바로 만드는 것이 좋다. 나중에 vector/hybrid를 한꺼번에 넣으면 변경 범위가 커진다.

### 2. `BookSearchResponse`에 점수 필드 추가 전략

문서 04에서 `similarity`, `rrfScore`를 추가하라고 했는데, 현재 `BookSearchResponse`는 생성자가 10개 파라미터다. 여기에 2개를 더 넣으면 12개가 되고, Step 4에서 리뷰 필드 3개를 더 넣으면 15개가 된다.

**추천**: Builder 패턴 도입을 이 시점에서 검토한다.

```java
@Builder
@Getter
public class BookSearchResponse {
    // 기본 필드 10개
    // Step 2 추가
    private Double similarity;
    private Double rrfScore;
}
```

Lombok `@Builder`를 쓰면 기존 `Projections.constructor()` 대신 `Projections.fields()` + setter나, native query 결과를 Builder로 조립하는 방식으로 바꿔야 한다. 이게 번거롭다면 `withSimilarity(Double)` 같은 복사 메서드를 추가하는 방식이 기존 코드 변경을 최소화한다.

```java
public BookSearchResponse withSimilarity(Double similarity) {
    BookSearchResponse copy = new BookSearchResponse(
        this.id, this.isbn, this.title, this.authorName,
        this.publisherName, this.price, this.editionPublishDate,
        this.imageUrl, this.bookContent, this.category
    );
    copy.similarity = similarity;
    return copy;
}
```

### 3. 임베딩 텍스트 빌더에서 `bookContent` 길이 제한이 빠져 있다

문서 03에서 임베딩 대상 텍스트 조합을 설명할 때, `bookContent` 전체를 넣는 것처럼 보인다. 실제 `bookContent`는 수천 자가 될 수 있고, BGE-M3도 입력 토큰 한계(8192 토큰)가 있다. 임베딩 텍스트 빌더에서 `bookContent`를 적절히 잘라야 한다.

```text
[소개] {bookContent 앞 500자}
```

이 기준을 문서 03의 전처리 섹션에 명시하거나, `BookEmbeddingTextBuilder`에서 `maxContentLength`를 설정값으로 빼는 것이 좋다.

### 4. `toPgVector()` 유틸리티의 Java 코드 실수 경고가 더 강조되어야 한다

문서 04에서 `Arrays.stream(float[])`가 없다는 점을 언급했는데, 이건 실제로 자주 빠지는 함정이다. 정확한 구현을 문서에 남겨두면 구현 시 시간을 아낄 수 있다.

```java
public static String toPgVector(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
        if (i > 0) sb.append(',');
        sb.append(vector[i]);
    }
    return sb.append(']').toString();
}
```

### 5. HYBRID 검색의 후보 수와 메모리 페이징

문서 05에서 각 검색에서 top 50을 가져와 메모리에서 병합한다고 했다. 이 방식은 정확하지만 한 가지 중요한 함정이 있다. **KEYWORD 검색의 top 50을 가져올 때 기존 `search()` 메서드는 `Pageable`을 받으므로, HYBRID에서 사용할 때는 별도로 `PageRequest.of(0, maxCandidates)`를 넘겨야 한다.** 기존 화면의 pageable을 그대로 쓰면 화면 page size(24)만큼만 가져와서 RRF 품질이 떨어진다.

```java
// HYBRID 내부에서
Pageable candidatePageable = PageRequest.of(0, maxCandidates); // 50
List<BookSearchResponse> keywordResults = bookRepository.search(candidatePageable, request).getContent();
List<BookSearchResponse> vectorResults = bookRepository.vectorSearch(candidatePageable, queryVector).getContent();
// RRF 병합 후 화면 pageable로 자르기
```

이 부분을 문서에 명시적으로 넣으면 구현 시 실수를 줄일 수 있다.

### 6. `retrieval-candidates` 설정이 누락되어 있다

문서 05에서 `app.ai.max-candidates=50`과 `app.ai.rrf-k=60`을 언급하지만, 현재 `application.properties`의 `app.ai.max-candidates=5`는 RAG Top-K 용도다. HYBRID 후보 수용 `app.ai.retrieval-candidates`가 아직 없다. Step 2 구현 시작 전에 이 값을 먼저 추가해야 한다.

```properties
# Step 2: HYBRID 검색 후보 수
app.ai.retrieval-candidates=50
# Step 3: RAG 컨텍스트 후보 수 (기존)
app.ai.max-candidates=5
```

### 우선순위 요약

| 우선순위 | 항목 | 시점 |
| --- | --- | --- |
| **1** | Service에 SearchType switch 분기 뼈대 | Step 2 첫 커밋 |
| **2** | `retrieval-candidates` 설정 추가 | Step 2 시작 전 |
| **3** | `BookSearchResponse`에 점수 필드 추가 전략 결정 | vectorSearch 구현 직전 |
| **4** | 임베딩 텍스트 `bookContent` 길이 제한 | 배치 구현 시 |
| **5** | `toPgVector()` 정확한 Java 구현 | vectorSearch 구현 시 |
| **6** | HYBRID 후보 pageable 분리 | HYBRID 구현 시 |
