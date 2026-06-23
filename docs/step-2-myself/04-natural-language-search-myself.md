# 04. 현재 프로젝트 자연어 벡터 검색 구현 보강

이 문서는 강의자료 `docs/step-2/04.natural-language-search.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 문서는 pgvector 연산자와 QueryDSL 예시를 보여준다. 현재 프로젝트에서는 `BookRepositoryCustom`이 아직 `search(...)` 하나만 가지고 있으므로, 벡터 검색 메서드를 명확히 분리해서 붙이는 편이 좋다.

## 목표 흐름

```text
사용자 keyword
  -> BookSearchService
      -> EmbeddingService.embed(keyword)
      -> BookRepository.vectorSearch(pageable, vector)
          -> PostgreSQL pgvector cosine distance
```

`VECTOR` 검색은 키워드 검색과 다르게 `keyword` 자체로 DB LIKE를 하지 않는다. 검색어를 먼저 1024차원 벡터로 변환하고, DB에 저장된 `books.embedding`과 비교한다.

## SearchType 분기

현재 `SearchType`에는 이미 `VECTOR`와 `HYBRID`가 있다.

```java
public enum SearchType {
    KEYWORD("keyword"),
    VECTOR("vector"),
    HYBRID("hybrid"),
    RAG("rag");
}
```

`BookSearchService`에서 먼저 분기한다.

```text
KEYWORD -> 기존 bookRepository.search(...)
VECTOR  -> embedding 생성 후 bookRepository.vectorSearch(...)
HYBRID  -> Step 2-5에서 구현
RAG     -> Step 3 이후
```

검색어가 비어 있으면 `VECTOR` 검색을 하지 않는다. 의미 검색은 입력 문장이 있어야 하므로 빈 검색어는 기존 전체 목록 또는 빈 결과 중 하나로 정책을 정해야 한다. 화면 UX 기준으로는 전체 목록을 보여주는 기존 동작이 더 자연스럽다.

## Repository 메서드 분리

`BookRepositoryCustom`에 벡터 검색을 별도 메서드로 추가하는 구조가 명확하다.

```java
Page<BookSearchResponse> vectorSearch(Pageable pageable, float[] queryVector);
```

`BookSearchRequest`에 `vector` 필드가 이미 있으므로 아래처럼 받을 수도 있다.

```java
Page<BookSearchResponse> vectorSearch(Pageable pageable, BookSearchRequest request);
```

다만 Repository는 검색어를 벡터로 바꾸는 책임을 알 필요가 없다. 가능하면 `float[] queryVector`를 직접 넘기는 편이 책임이 선명하다.

## pgvector SQL 기준

코사인 거리 기준 SQL은 아래 형태다.

강의자료의 projection 예시에 `volumeTitle`이 있더라도 현재 프로젝트에서는 제외한다. 현재 목록 응답은 `category`를 내려주는 구조이므로 벡터 검색 SQL도 `category`를 포함해야 한다.

```sql
SELECT id,
       isbn,
       title,
       author_name,
       publisher_name,
       price,
       edition_publish_date,
       image_url,
       book_content,
       category,
       1 - (embedding <=> :queryVector::vector) AS similarity
FROM books
WHERE embedding IS NOT NULL
ORDER BY embedding <=> :queryVector::vector
LIMIT :limit OFFSET :offset;
```

`<=>`는 코사인 거리다. 값이 작을수록 가깝다. 화면에는 보통 `1 - distance`를 similarity로 보여준다.

## QueryDSL vs Native SQL

벡터 검색은 PostgreSQL 전용 연산자인 `<=>`를 써야 한다. QueryDSL로도 `Expressions.numberTemplate`을 사용할 수 있지만, 처음 구현은 native SQL이 더 단순하고 디버깅하기 쉽다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| QueryDSL template | 기존 Repository 스타일 유지 | vector cast, 함수 등록이 까다로움 |
| Native SQL | 실제 SQL과 거의 동일 | DTO 매핑 코드가 필요 |
| JdbcTemplate | pgvector 문자열 바인딩이 단순 | JPA Page 조립을 직접 해야 함 |

현재 프로젝트가 QueryDSL 기반이므로 최종적으로는 QueryDSL에 맞춰도 된다. 하지만 첫 동작 확인은 PostgreSQL에서 SQL을 직접 실행해 검증한 뒤 Java 코드로 옮기는 순서가 안전하다.

## vector 파라미터 포맷

pgvector는 보통 아래 문자열 형태를 받을 수 있다.

```text
[0.1,0.2,0.3]
```

Java `float[]`를 SQL에 넘길 때는 이 문자열로 변환하는 유틸리티를 두면 편하다.

```java
public String toPgVector(float[] vector) {
    return Arrays.stream(vector)
            .mapToObj(Float::toString)
            .collect(Collectors.joining(",", "[", "]"));
}
```

실제 Java에서는 `Arrays.stream(float[])`가 없으므로 for loop나 IntStream을 써야 한다. 이 부분을 대충 작성하면 컴파일 오류가 나기 쉽다.

## DTO에 점수 추가

현재 `BookSearchResponse`에는 similarity가 없다.

```java
private Double similarity;
```

벡터 검색 결과를 화면에서 구분하려면 아래 필드를 추가하는 편이 좋다.

```text
similarity: 벡터 검색 유사도
rrfScore: 하이브리드 검색 점수
```

다만 필드를 추가하면 기존 `Projections.constructor(...)` 생성자 순서도 같이 맞춰야 한다. 기존 키워드 검색 projection이 깨지지 않도록 기존 생성자는 유지하고, similarity까지 받는 생성자를 추가하는 방식이 안전하다.

## count 쿼리 주의

벡터 검색에서 전체 count를 어떻게 볼지 정해야 한다.

```text
옵션 1. embedding IS NOT NULL 전체 count
옵션 2. similarity threshold 이상 count
옵션 3. topK만 가져오고 Page가 아니라 Slice/List로 반환
```

검색 품질 관점에서는 벡터 검색을 무한 페이징하는 것보다 topK 결과만 보는 경우가 많다. 현재 화면이 `Page`를 기대하므로 처음에는 `embedding IS NOT NULL` count로 맞출 수 있지만, 실제 의미 검색에서는 상위 20-50개만 보여주는 정책이 더 자연스럽다.

## similarity threshold

초기 구현에서는 threshold를 넣지 말고 정렬 결과를 확인한다.

결과가 너무 넓으면 나중에 아래 조건을 추가한다.

```sql
WHERE embedding IS NOT NULL
  AND 1 - (embedding <=> :queryVector::vector) >= 0.3
```

threshold 값은 모델과 데이터에 따라 다르다. 문서에서 임의로 정한 값을 코드에 박아 넣기보다 설정값으로 빼는 편이 좋다.

## 완료 기준

```text
1. 검색어를 EmbeddingModel로 1024차원 벡터로 바꾼다.
2. embedding IS NOT NULL인 책만 벡터 검색 대상이 된다.
3. pgvector `<=>` 기준으로 가까운 결과가 먼저 나온다.
4. similarity를 응답 DTO에 담을 수 있다.
5. KEYWORD 검색 기존 동작이 깨지지 않는다.
```

이 단계에서는 아직 하이브리드 검색을 섞지 않는다. VECTOR 단독 검색 결과를 먼저 눈으로 확인해야 Step 2-5에서 RRF 병합 품질을 판단할 수 있다.
