# 06. Step 4 직접 구현 TODO 가이드

이 문서는 Step 4를 직접 구현하면서 채워 넣을 코드 뼈대다. 완성 코드를 복사하는 목적이 아니라, 각 TODO를 채우며 구조를 설명할 수 있게 만드는 목적이다.

## 1. AiProperties

`app.ai.*` 설정을 여러 서비스에서 `@Value`로 흩뜨리지 않는다.

```java
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        int retrievalCandidates,
        int maxCandidates,
        int fallbackCandidates,
        int minRelevanceScore,
        int rrfK,
        int maxReviewSummaryLength
) {
    public AiProperties {
        // TODO: 각 값이 0 이하일 때 기본값을 넣는다.
        // retrievalCandidates 기본값: 50
        // maxCandidates 기본값: 5
        // fallbackCandidates 기본값: 3
        // minRelevanceScore 기본값: 50
        // rrfK 기본값: 60
        // maxReviewSummaryLength 기본값: 100
    }
}
```

```java
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiPropertiesConfig {
}
```

`application.properties`에 HYBRID 후보 수를 추가한다.

```properties
# Step 2/4: HYBRID 검색 후보 수
app.ai.retrieval-candidates=50
```

## 2. 성능 측정 AOP

`System.currentTimeMillis()`를 모든 서비스에 직접 넣지 않기 위한 구조다.

필요 의존성:

```xml
<!-- TODO: pom.xml에 없으면 추가한다. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasureTime {
    String value() default "";
}
```

```java
@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    @Around("@annotation(measureTime)")
    public Object measure(ProceedingJoinPoint joinPoint, MeasureTime measureTime) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            // TODO: measureTime.value()가 있으면 로그 이름으로 우선 사용한다.
            // TODO: 클래스명, 메서드명, elapsed ms를 남긴다.
        }
    }
}
```

적용 예:

```java
@MeasureTime("rag-search")
public BookRagResult recommend(String question) {
    // TODO: RAG 추천 흐름 구현
    return null;
}
```

## 3. Top-K 분리

HYBRID 후보 수와 RAG 컨텍스트 후보 수를 분리한다.

```java
@Service
@RequiredArgsConstructor
public class RagCandidateSelector {

    private final AiProperties aiProperties;

    public List<BookSearchResponse> selectForRag(List<BookSearchResponse> hybridResults) {
        // TODO: rrfScore가 있으면 점수순 정렬을 보장한다.
        // TODO: relevance나 rrfScore 기준 필터링을 적용할지 결정한다.
        // TODO: 결과가 비면 fallbackCandidates만큼 반환한다.
        // TODO: 최종적으로 maxCandidates 이하로 자른다.
        return List.of();
    }
}
```

설명할 수 있어야 하는 것:

```text
retrievalCandidates=50은 검색 후보 수다.
maxCandidates=5는 LLM에 넘기는 컨텍스트 후보 수다.
둘을 같은 값으로 두면 어떤 문제가 생기는가?
```

## 4. 캐시 키 정규화

동일 질문 캐시는 정규화 기준부터 정한다.

```java
@Component
public class RagCacheKeyGenerator {

    public String key(String query, SearchType searchType, int ragK) {
        // TODO: version을 상수로 둔다. 예: rag:v1
        // TODO: normalizeQuery(query)를 적용한다.
        // TODO: query가 너무 길면 hash를 사용할지 결정한다.
        return null;
    }

    String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
}
```

1단계 캐시 서비스:

```java
@Service
@RequiredArgsConstructor
public class RagExactCacheService {

    private final RagCacheKeyGenerator keyGenerator;
    // TODO: Caffeine Cache 또는 RedisTemplate 중 하나를 선택한다.

    public Optional<BookRagResult> get(String query, SearchType searchType, int ragK) {
        // TODO: key 생성 후 캐시를 조회한다.
        return Optional.empty();
    }

    public void put(String query, SearchType searchType, int ragK, BookRagResult result) {
        // TODO: TTL 30분 기준으로 저장한다.
    }
}
```

## 5. 리뷰 엔티티

`Book` 엔티티에 `@OneToOne`을 바로 추가하지 않는다. 리뷰 요약은 분리 조회한다.

```java
@Entity
@Table(name = "book_reviews")
@Getter
@NoArgsConstructor
public class BookReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // TODO: 생성자에서 rating 1~5 검증
    // TODO: @PrePersist로 createdAt 설정
}
```

```java
@Entity
@Table(name = "book_review_summaries")
@Getter
@NoArgsConstructor
public class BookReviewSummary {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    private Long reviewCount;
    private BigDecimal averageRating;

    @Column(columnDefinition = "TEXT")
    private String reviewSummary;

    @Column(nullable = false)
    private boolean summaryDirty;

    @Version
    private Long version;

    // TODO: 리뷰가 추가될 때 통계를 갱신하는 메서드
    // TODO: 요약 완료 시 reviewSummary 저장, summaryDirty=false 처리
    // TODO: 요약 실패 시 summaryDirty를 유지할지 결정
}
```

## 6. RabbitMQ 요약 멱등성

같은 `bookId` 메시지가 여러 번 와도 같은 결과가 되게 만든다.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewSummaryConsumer {

    private final ReviewSummaryService reviewSummaryService;

    @RabbitListener(queues = "${rabbitmq.queue.review-summary}")
    public void consume(Long bookId) {
        // TODO: bookId null 방어
        // TODO: reviewSummaryService.summarize(bookId) 호출
        // TODO: OptimisticLockException 발생 시 재시도/로그 정책 결정
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    private final BookReviewSummaryRepository summaryRepository;

    @Transactional
    public void summarize(Long bookId) {
        BookReviewSummary summary = summaryRepository.findById(bookId)
                .orElseThrow();

        if (!summary.isSummaryDirty()) {
            // TODO: 이미 다른 consumer가 처리했으므로 return
            return;
        }

        // TODO: 리뷰 목록을 조회한다.
        // TODO: LLM으로 요약한다.
        // TODO: summary.updateSummary(result)를 호출한다.
    }
}
```

## 7. 리뷰 RAG 통합

검색 결과 전체가 아니라 RAG topK 후보에만 리뷰 정보를 붙인다.

```java
@Service
@RequiredArgsConstructor
public class RagReviewContextAppender {

    private final BookReviewSummaryRepository summaryRepository;
    private final AiProperties aiProperties;

    public Map<Long, BookReviewSummary> findSummaries(List<BookSearchResponse> topKBooks) {
        // TODO: book id 목록을 만든다.
        // TODO: findByBookIdIn(ids) 형태로 한 번에 조회한다.
        // TODO: bookId -> summary Map으로 반환한다.
        return Map.of();
    }

    public String formatReviewInfo(BookReviewSummary summary) {
        // TODO: summary가 null이면 빈 문자열 반환
        // TODO: averageRating, reviewCount, reviewSummary를 짧게 포맷
        // TODO: reviewSummary는 maxReviewSummaryLength로 자른다.
        return "";
    }
}
```

## 8. 직접 설명 체크리스트

```text
왜 AiProperties가 필요한가?
왜 Retrieval K와 RAG K를 분리하는가?
왜 캐시 key에 version이 들어가는가?
왜 BookReviewSummary를 Book 엔티티에 바로 붙이지 않는가?
RabbitMQ consumer가 같은 bookId를 두 번 받아도 왜 안전해야 하는가?
리뷰 평점이 높아도 질문과 관련 없으면 추천하면 안 되는 이유는 무엇인가?
```
