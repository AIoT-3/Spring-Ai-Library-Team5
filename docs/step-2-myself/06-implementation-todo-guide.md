# 06. Step 2 직접 구현 TODO 가이드

이 문서는 Step 2를 직접 구현하면서 채워 넣을 코드 뼈대다. 완성 코드를 복사하는 목적이 아니라, 각 TODO를 채우며 구조를 설명할 수 있게 만드는 목적이다.

## 1. BookSearchService SearchType 분기

현재 모든 요청이 `bookRepository.search(...)`로만 간다. Step 2 첫 커밋에서 분기 뼈대를 먼저 잡는다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookSearchService {

    private final BookRepository bookRepository;
    // TODO: Step 2 임베딩 구현 후 주입한다.
    // private final EmbeddingService embeddingService;

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return switch (request.searchType()) {
            case KEYWORD -> keywordSearch(pageable, request);
            case VECTOR -> {
                // TODO: vectorSearch를 구현하면 여기를 채운다.
                // 지금은 KEYWORD로 fallback한다.
                yield keywordSearch(pageable, request);
            }
            case HYBRID -> {
                // TODO: hybridSearch를 구현하면 여기를 채운다.
                yield keywordSearch(pageable, request);
            }
            case RAG -> {
                // TODO: Step 3에서 BookRagService로 위임한다.
                yield keywordSearch(pageable, request);
            }
        };
    }

    private BookSearchResult keywordSearch(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(bookRepository.search(pageable, request));
    }
}
```

설명할 수 있어야 하는 것:

```text
왜 처음부터 switch를 만들고 fallback을 두는가?
RAG에서 왜 KEYWORD가 아니라 HYBRID를 기반으로 하는가?
검색어가 비어 있으면 VECTOR 검색을 해도 되는가?
```

## 2. retrieval-candidates 설정 추가

`application.properties`에 HYBRID 후보 수를 추가한다. 기존 `max-candidates=5`는 RAG용이다.

```properties
# Step 2: HYBRID 검색에서 각 검색 방식이 가져올 후보 수
app.ai.retrieval-candidates=50
```

코드에서는 `@Value`로 읽거나, Step 4에서 `AiProperties`로 묶을 때까지 임시로 사용한다.

```java
// TODO: Step 4에서 AiProperties record로 이동한다.
@Value("${app.ai.retrieval-candidates:50}")
private int retrievalCandidates;
```

## 3. EmbeddingService

외부 모델 호출만 담당한다. DB 조회나 전처리를 여기에 넣지 않는다.

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        // TODO: text가 blank면 예외를 던질지 빈 배열을 반환할지 정한다.
        // TODO: embeddingModel.embed(text)를 호출한다.
        // TODO: 결과 차원이 1024가 아니면 예외를 던진다.
        return null;
    }
}
```

설명할 수 있어야 하는 것:

```text
왜 EmbeddingService에 Book 로직을 넣지 않는가?
차원 검증을 왜 저장할 때가 아니라 생성 직후에 하는가?
```

## 4. BookEmbeddingTextBuilder

`Book`을 임베딩용 문자열로 바꾸는 책임만 담당한다. `volumeTitle`은 사용하지 않는다.

```java
@Component
public class BookEmbeddingTextBuilder {

    private static final int MAX_CONTENT_LENGTH = 500;

    public String build(Book book) {
        // TODO: 각 필드에 라벨을 붙여 조합한다.
        // [제목] {title}
        // [부제] {subtitle}
        // [저자] {authorName}
        // [출판사] {publisherName}
        // [분류] {category}
        // [소개] {bookContent 앞 MAX_CONTENT_LENGTH자}
        return null;
    }

    private String safe(String value) {
        // TODO: null이면 빈 문자열을 반환한다.
        return value;
    }

    private String truncate(String value, int maxLength) {
        // TODO: null/blank면 빈 문자열
        // TODO: 연속 공백 정규화
        // TODO: maxLength 초과 시 잘라서 "..." 붙이기
        return value;
    }
}
```

설명할 수 있어야 하는 것:

```text
왜 bookContent를 500자로 자르는가?
라벨([제목], [저자])을 붙이는 이유는 무엇인가?
나중에 "본문을 빼면 검색 품질이 좋아지는가" 실험을 하려면 어디를 바꾸면 되는가?
```

## 5. TextPreprocessor

임베딩 전 텍스트 정제를 담당한다. 과하게 하지 않는다.

```java
@Component
public class TextPreprocessor {

    public String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // TODO: HTML entity decode (예: &amp; → &)
        // TODO: HTML tag 제거 (예: <b>text</b> → text)
        // TODO: 연속 공백을 하나로 정규화
        // TODO: 앞뒤 공백 제거
        // TODO: C++, C#, Node.js, GPT-4 같은 표현을 보존해야 한다.
        //       특수문자를 전부 제거하는 정규식은 쓰지 않는다.
        return text;
    }
}
```

## 6. 임베딩 배치 서비스

`embedding IS NULL`인 책만 처리한다. 실패해도 재실행하면 남은 것만 이어서 처리된다.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BookEmbeddingBatchService {

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final BookEmbeddingTextBuilder textBuilder;
    private final TextPreprocessor preprocessor;

    @Value("${app.batch.embedding-size:32}")
    private int batchSize;

    @Transactional
    public int processNextBatch() {
        // TODO: embedding IS NULL인 책을 id 오름차순으로 batchSize개 조회한다.
        //       offset pagination이 아니라 "embedding IS NULL ORDER BY id LIMIT N" 방식을 쓴다.
        // TODO: 각 책에 대해:
        //   1. textBuilder.build(book)으로 임베딩 텍스트를 만든다.
        //   2. preprocessor.clean()으로 정제한다.
        //   3. 빈 텍스트면 skip하고 로그를 남긴다.
        //   4. embeddingService.embed()로 벡터를 생성한다.
        //   5. 차원이 1024가 아니면 예외를 던진다.
        //   6. book.updateEmbedding(embedding)으로 저장한다.
        //   7. 실패한 book의 id와 이유를 로그로 남긴다.
        // TODO: 처리 건수를 반환한다.
        return 0;
    }
}
```

임베딩 대상 조회 쿼리를 `BookRepository`에 추가한다.

```java
public interface BookRepository extends JpaRepository<Book, Long>, BookRepositoryCustom {

    // TODO: embedding이 null인 책을 id 오름차순으로 N개 조회하는 메서드를 추가한다.
    // @Query("SELECT b FROM Book b WHERE b.embedding IS NULL ORDER BY b.id")
    // List<Book> findBooksWithoutEmbedding(Pageable pageable);
}
```

설명할 수 있어야 하는 것:

```text
왜 offset pagination 대신 "WHERE embedding IS NULL" 반복 방식을 쓰는가?
chunk 단위로 커밋하면 어떤 장점이 있는가?
임베딩 API가 실패해도 전체 배치가 멈추지 않으려면 어떻게 해야 하는가?
```

## 7. pgvector 유틸리티

Java `float[]`를 pgvector 문자열로 변환한다. `Arrays.stream(float[])`는 존재하지 않으므로 for loop를 써야 한다.

```java
public class PgVectorUtils {

    private PgVectorUtils() {}

    public static String toPgVector(float[] vector) {
        // TODO: "[0.1,0.2,0.3,...]" 형태의 문자열로 변환한다.
        // TODO: Arrays.stream(float[])는 없다. IntStream이나 for loop를 쓴다.
        // TODO: null이면 예외를 던질지 빈 문자열을 반환할지 결정한다.
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
```

## 8. BookRepositoryCustom에 vectorSearch 추가

벡터 검색은 키워드 검색과 별도 메서드로 분리한다.

```java
@NoRepositoryBean
public interface BookRepositoryCustom {

    Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request);

    // TODO: 벡터 검색 메서드를 추가한다.
    // Repository는 검색어를 벡터로 바꾸는 책임을 모르므로 float[]을 직접 받는다.
    // Page<BookSearchResponse> vectorSearch(Pageable pageable, float[] queryVector);
}
```

## 9. BookRepositoryImpl에 vectorSearch 구현

pgvector `<=>` 연산자를 사용하는 native SQL로 시작한다. QueryDSL template은 나중에 검토한다.

```java
@Override
public Page<BookSearchResponse> vectorSearch(Pageable pageable, float[] queryVector) {
    // TODO: queryVector를 PgVectorUtils.toPgVector()로 문자열 변환한다.
    // TODO: native SQL로 cosine distance 기준 정렬 조회를 한다.
    //   SELECT id, isbn, title, author_name, publisher_name, price,
    //          edition_publish_date, image_url, book_content, category,
    //          1 - (embedding <=> :queryVector::vector) AS similarity
    //   FROM books
    //   WHERE embedding IS NOT NULL
    //   ORDER BY embedding <=> :queryVector::vector
    //   LIMIT :limit OFFSET :offset
    // TODO: 결과를 BookSearchResponse로 매핑한다.
    //   이때 similarity를 어떻게 전달할지 결정한다.
    //   - 생성자 확장 vs withSimilarity() 복사 메서드
    // TODO: count 쿼리는 "embedding IS NOT NULL" 기준으로 한다.
    //   벡터 검색을 무한 페이징할지, topK만 보여줄지 정책을 정한다.
    return null;
}
```

설명할 수 있어야 하는 것:

```text
<=>는 무엇을 계산하는가? 값이 작으면 가까운가 먼가?
1 - distance를 similarity로 쓰는 이유는 무엇인가?
왜 처음에 native SQL로 시작하는가?
```

## 10. BookSearchResponse에 점수 필드 추가

기존 생성자를 깨뜨리지 않으면서 점수 필드를 추가한다.

```java
@Getter
@NoArgsConstructor
public class BookSearchResponse {

    // 기존 10개 필드 유지

    // TODO: 아래 필드를 추가한다.
    // private Double similarity;
    // private Double rrfScore;

    // TODO: 기존 10개 파라미터 생성자는 유지한다. (KEYWORD 검색 projection용)

    // TODO: 점수를 추가하는 방식을 하나 선택한다.
    //   방식 A: 복사 메서드
    //   public BookSearchResponse withSimilarity(Double similarity) {
    //       BookSearchResponse copy = new BookSearchResponse(
    //           this.id, this.isbn, this.title, ...
    //       );
    //       copy.similarity = similarity;
    //       return copy;
    //   }
    //
    //   방식 B: 점수 포함 생성자 추가
    //   public BookSearchResponse(Long id, ..., String category, Double similarity) { ... }
    //
    //   방식 C: @Builder 도입 (기존 Projections.constructor 사용 부분도 같이 변경)
}
```

설명할 수 있어야 하는 것:

```text
왜 기존 생성자를 유지하면서 필드를 추가해야 하는가?
Projections.constructor() 순서가 바뀌면 어떤 오류가 생기는가?
Step 4에서 리뷰 필드 3개가 더 추가되면 이 구조가 어떻게 되는가?
```

## 11. HYBRID 검색 (RRF 병합)

키워드 검색과 벡터 검색의 결과를 RRF로 합친다.

```java
// BookSearchService 안에 private 메서드로 시작한다.
private BookSearchResult hybridSearch(Pageable pageable, BookSearchRequest request) {

    // TODO: 검색어가 비어 있으면 KEYWORD로 fallback한다.

    // TODO: HYBRID 후보용 Pageable을 별도로 만든다.
    //   화면의 pageable(size=24)을 그대로 쓰면 후보가 적어서 RRF 품질이 떨어진다.
    //   Pageable candidatePageable = PageRequest.of(0, retrievalCandidates);

    // TODO: 키워드 검색 top N을 가져온다.
    // TODO: 검색어를 embedding으로 변환한다.
    // TODO: 벡터 검색 top N을 가져온다.
    // TODO: rrfMerge()로 병합한다.
    // TODO: 화면 pageable 기준으로 잘라서 Page를 만든다.

    return keywordSearch(pageable, request);
}
```

RRF 병합 메서드:

```java
private List<BookSearchResponse> rrfMerge(
        List<BookSearchResponse> keywordResults,
        List<BookSearchResponse> vectorResults,
        int rrfK
) {
    // TODO: Map<Long, BookSearchResponse> bookMap을 만든다.
    // TODO: Map<Long, Double> rrfScoreMap을 만든다.

    // TODO: keywordResults 순회
    //   - bookMap에 없으면 저장
    //   - rrfScoreMap에 1/(rrfK + rank) 누적 (rank는 1부터)

    // TODO: vectorResults 순회
    //   - bookMap에 없으면 저장
    //   - 이미 있으면 similarity 등 vector 관련 점수를 보존
    //   - rrfScoreMap에 1/(rrfK + rank) 누적

    // TODO: 각 BookSearchResponse에 rrfScore를 세팅한다.
    //   withRrfScore() 메서드를 쓸지, setter를 쓸지 결정한다.

    // TODO: rrfScore desc 정렬 후 반환한다.

    return List.of();
}

private double rrfScore(int rank, int k) {
    return 1.0 / (k + rank);
}
```

설명할 수 있어야 하는 것:

```text
왜 키워드 점수와 벡터 similarity를 직접 더하면 안 되는가?
RRF에서 k=60의 의미는 무엇인가? k를 크게 하면 어떻게 되는가?
keyword-only 결과와 vector-only 결과가 병합에서 빠지면 안 되는 이유는 무엇인가?
isbn이 들어왔을 때 왜 HYBRID보다 KEYWORD 정확 검색이 나은가?
```

## 12. RRF 단위 테스트

RRF 병합은 DB 없이 순수 Java로 테스트할 수 있다.

```java
@Test
void rrfMerge는_양쪽에_있는_책이_더_높은_점수를_받는다() {
    // TODO: keyword 결과: [A, B, C]
    // TODO: vector 결과: [C, A, D]
    // TODO: 병합 결과:
    //   A와 C는 양쪽에 있으므로 점수가 누적된다.
    //   B와 D는 한쪽에만 있으므로 점수가 낮다.
    //   A와 C가 상위에 온다.
    //   중복 id는 하나만 존재한다.
}

@Test
void rrfMerge는_빈_결과에도_예외를_던지지_않는다() {
    // TODO: keyword 빈 리스트, vector 빈 리스트 → 빈 결과
    // TODO: keyword만 있고 vector 빈 리스트 → keyword 순서 유지
}
```

## 13. 직접 설명 체크리스트

```text
KEYWORD, VECTOR, HYBRID의 차이를 한 문장씩 설명할 수 있는가?
왜 embedding IS NULL인 책은 벡터 검색에서 제외되는가?
toPgVector()에서 Arrays.stream(float[])를 쓰면 안 되는 이유는 무엇인가?
RRF 병합에서 후보 Pageable을 화면 Pageable과 분리하는 이유는 무엇인가?
벡터 검색에서 count 쿼리를 embedding IS NOT NULL 전체로 하면 어떤 문제가 있는가?
BookSearchResponse에 similarity를 추가할 때 기존 생성자를 유지해야 하는 이유는 무엇인가?
임베딩 텍스트에 bookContent 전체를 넣으면 어떤 문제가 생기는가?
H2에서 pgvector <=> 연산자를 테스트할 수 없는 이유는 무엇인가?
```
