# Step 4 Myself

이 패키지는 `docs/step-4` 강의자료를 현재 프로젝트 기준으로 보강한 문서 모음이다.

Step 4의 목표는 Step 3 RAG를 운영 가능한 수준으로 다듬는 것이다. 비용, 속도, Top-K, 캐시, 리뷰 요약을 한 번에 구현하기보다 단계별로 나눠 적용한다.

## 챕터 구성

| 원본 | 보강 문서 | 핵심 보강 |
| --- | --- | --- |
| `01.performance-cost.md` | `01-performance-cost-myself.md` | 현재 설정 기준 병목과 측정 지표 |
| `02.topk-optimization.md` | `02-topk-optimization-myself.md` | Retrieval K/RAG K 분리, `category` 컨텍스트 |
| `03.semantic-caching.md` | `03-semantic-caching-myself.md` | Caffeine/Redis 선택, 캐시 키와 무효화 |
| `04.review-summarization.md` | `04-review-summarization-myself.md` | 리뷰 도메인 추가 순서, 비동기 요약 |
| `05.review-rag-integration.md` | `05-review-rag-integration-myself.md` | 검색 DTO 확장과 리뷰 정보 컨텍스트 반영 |
| 추가 | `06-implementation-todo-guide.md` | 직접 구현용 TODO 코드 뼈대 |

## 구현 권장 순서

```text
1. RAG 요청의 검색 시간, LLM 시간, 컨텍스트 길이를 로그로 남긴다.
2. Retrieval K와 RAG K를 설정값으로 분리한다.
3. 동일 질문 캐시를 먼저 붙이고, 이후 시맨틱 캐시로 확장한다.
4. 리뷰 도메인과 리뷰 통계를 별도로 구현한다.
5. 리뷰 요약은 비동기 처리로 분리한다.
6. 마지막에 리뷰 요약을 RAG 컨텍스트에 포함한다.
```

현재 프로젝트에는 리뷰 UI와 설정값 일부가 있지만 Java 도메인/서비스는 아직 없으므로, 문서에서는 추가해야 할 경계를 명확히 한다.

## 의존 그래프

```text
01 성능 측정 ──┬──→ 02 Top-K 분리
              └──→ 03 캐시

04 리뷰 도메인/요약 ───→ 05 리뷰 RAG 통합
                              ↑
                     02 Top-K 정리 이후 붙이는 것이 안전
```

## 학습 방식

Step 4는 한 번에 전부 구현하면 설명하기 어려워진다. 아래 단위로 끊어서 구현한다.

```text
1. AiProperties로 설정값을 묶고, 각 값이 어디에 쓰이는지 설명한다.
2. 성능 측정은 AOP TODO를 직접 채워서 로그를 확인한다.
3. Top-K는 Retrieval K와 RAG K를 직접 바꿔 보며 컨텍스트 길이를 비교한다.
4. 캐시는 동일 질문 캐시부터 구현하고, 시맨틱 캐시는 두 번째 단계로 미룬다.
5. 리뷰는 BookReview와 BookReviewSummary를 Book 엔티티와 분리해서 만든다.
6. RabbitMQ 요약은 dirty flag와 @Version으로 멱등성을 설명할 수 있게 만든다.
7. 리뷰 RAG 통합은 topK 후보에만 리뷰 요약을 붙인다.
```

완성된 기능보다 중요한 것은 “이 최적화가 어떤 비용을 줄이고, 어떤 위험을 만든다”를 말할 수 있는 것이다.

---

## 코드 리뷰 의견

### 총평

Step 4 문서가 가장 넓은 범위를 다루고 있다. 성능 측정, Top-K 분리, 캐시, 리뷰 도메인, 리뷰 RAG 통합까지 5개 챕터다. 문서 자체의 순서와 경계 설정은 잘 되어 있지만, 이 양을 구현할 때 순서를 잘못 잡으면 중간에 꼬이기 쉽다. 아래 리뷰는 "구현 순서와 설계 결정"에 집중한다.

### 1. 성능 로그를 AOP 또는 인터셉터로 한 번에 잡는 것을 고려한다

문서 01에서 각 단계별 시간 측정을 로그로 남기라고 했다. 이걸 각 메서드마다 `System.currentTimeMillis()`로 넣으면 코드가 금방 지저분해진다.

**추천**: 간단한 커스텀 어노테이션 + AOP로 메서드 실행 시간을 일괄 측정한다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasureTime {
    String value() default "";
}

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
            log.info("[PERF] {}#{} elapsed={}ms",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                System.currentTimeMillis() - start);
        }
    }
}
```

이러면 `@MeasureTime`을 붙이기만 하면 되고, Step 4 이후 운영 모니터링으로 전환할 때도 한 곳만 수정하면 된다. 다만 AOP를 쓰려면 `spring-boot-starter-aop` 의존성이 필요한데, 현재 `pom.xml`에는 없다. 추가가 필요하다.

### 2. Retrieval K와 RAG K 분리를 설정 클래스로 묶어야 한다

문서 02에서 `app.ai.retrieval-candidates`와 `app.ai.max-candidates`를 분리하라고 했다. 현재 `application.properties`에 `app.ai.*` 관련 값이 5개 이상 있다. 이걸 각각 `@Value`로 읽으면 어디서 뭘 쓰는지 추적이 어렵다.

**추천**: `@ConfigurationProperties`로 묶는다.

```java
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    int retrievalCandidates,    // 50
    int maxCandidates,          // 5
    int fallbackCandidates,     // 3
    int minRelevanceScore,      // 50
    int rrfK,                   // 60
    int maxReviewSummaryLength  // 100
) {
    public AiProperties {
        if (retrievalCandidates <= 0) retrievalCandidates = 50;
        if (maxCandidates <= 0) maxCandidates = 5;
        if (rrfK <= 0) rrfK = 60;
    }
}
```

Step 1 코드 리뷰에서도 `InitProperties`를 record로 바꾸는 것을 제안했는데, 같은 패턴이다. `app.ai.*` 설정이 서비스 여러 곳에서 쓰이므로 하나의 Properties 객체로 주입하면 의존성이 명확해진다.

### 3. 캐시 단계 분리 전략이 좋다 — 다만 1단계 캐시 키 설계에 함정이 있다

문서 03에서 "1단계 동일 질문 캐시 → 2단계 시맨틱 캐시" 순서를 제안한 것은 매우 좋은 전략이다. 다만 1단계 캐시 키에 주의할 점이 있다.

```text
rag:v1:{normalizedQuery}:{searchType}:{ragK}
```

여기서 `normalizedQuery`의 정규화 기준이 빠져 있다. 같은 의미인데 캐시를 못 타는 경우가 생긴다.

```text
"자바 입문서 추천" ← 뒤에 공백
"자바  입문서  추천" ← 중복 공백
"자바 입문서 추천해줘" ← 어미 차이 → 시맨틱 캐시에서 처리
```

**추천**: 최소한 아래 정규화를 적용한다.

```java
private String normalizeQuery(String query) {
    return query.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase();
}
```

대소문자 통일(`toLowerCase`)은 한글 검색에서는 영향이 적지만, 영문 키워드가 섞일 수 있으므로 넣는 편이 안전하다.

### 4. 리뷰 도메인 구현 시 `BookReviewSummary`를 `Book`과 분리 테이블로 두는 것이 맞다

문서 04에서 `BookReview`와 `BookReviewSummary`를 별도 엔티티로 제안한 것은 좋다. 다만 `BookReviewSummary`를 `Book` 엔티티에 `@OneToOne`으로 매핑하면 `Book`을 조회할 때마다 항상 추가 쿼리가 발생한다(lazy loading이 1:1에서는 잘 안 먹힌다).

**추천**: `BookReviewSummary`는 `bookId`를 FK로 가지되, `Book` 엔티티에서 직접 참조하지 않는다. 필요할 때 `BookReviewSummaryRepository.findByBookId(bookId)`로 명시적으로 조회한다.

```java
@Entity
@Table(name = "book_review_summaries")
public class BookReviewSummary {

    @Id
    @Column(name = "book_id")
    private Long bookId;  // Book의 PK를 그대로 사용

    // ... 나머지 필드
}
```

이러면 기존 `Book` 엔티티와 `BookRepository`에 변경이 없다. Step 1에서 만든 배치나 검색이 리뷰 추가로 인해 깨질 위험이 없다.

### 5. RabbitMQ 비동기 요약에서 멱등성 처리가 더 구체적이어야 한다

문서 04에서 `summary_dirty` 플래그와 `version`으로 중복 처리를 방어한다고 했다. 하지만 실제 RabbitMQ 메시지 처리에서는 "같은 bookId에 대한 메시지가 동시에 2개 들어오면 어떻게 되는가"를 명확히 해야 한다.

**추천**: `@RabbitListener`에서 낙관적 잠금(Optimistic Locking)을 사용한다.

```java
@Transactional
public void summarize(Long bookId) {
    BookReviewSummary summary = summaryRepository.findByBookId(bookId)
        .orElseThrow();

    if (!summary.isSummaryDirty()) {
        log.info("Summary already up to date. bookId={}", bookId);
        return;  // 이미 다른 consumer가 처리함
    }

    // LLM 요약 실행
    String result = aiService.summarizeReviews(bookId);
    summary.updateSummary(result);
    // summary_dirty = false, version++
}
```

`@Version` 필드가 있으면 동시 업데이트 시 `OptimisticLockException`이 발생하고, RabbitMQ가 재시도할 수 있다. 문서에 이 흐름을 명시하면 구현 시 실수가 줄어든다.

### 6. 리뷰 RAG 통합에서 `BookSearchResponse` 생성자 폭발을 방지해야 한다

문서 05에서 기존 생성자를 유지하면서 리뷰 포함 생성자를 추가하라고 했다. 하지만 이미 Step 2에서 `similarity`, `rrfScore`를 추가했고, 여기서 다시 `averageRating`, `reviewCount`, `reviewSummary`를 추가하면 생성자가 3개 이상이 된다.

**이건 Step 2 리뷰에서 제안한 Builder 패턴이 여기서 빛을 발하는 지점이다.**

만약 Step 2에서 Builder를 도입하지 않았다면, 이 시점에서 반드시 도입해야 한다. 또는 `record` 기반으로 DTO를 재설계한다.

```java
public record BookSearchResponse(
    Long id,
    String isbn,
    String title,
    String authorName,
    String publisherName,
    BigDecimal price,
    LocalDate editionPublishDate,
    String imageUrl,
    String bookContent,
    String category,
    // Step 2 추가
    Double similarity,
    Double rrfScore,
    // Step 4 추가
    Double averageRating,
    Integer reviewCount,
    String reviewSummary
) {
    // 기본 검색용 compact 생성자
    public BookSearchResponse(Long id, String isbn, String title, String authorName,
            String publisherName, BigDecimal price, LocalDate editionPublishDate,
            String imageUrl, String bookContent, String category) {
        this(id, isbn, title, authorName, publisherName, price, editionPublishDate,
             imageUrl, bookContent, category, null, null, null, null, null);
    }
}
```

record로 바꾸면 `Projections.constructor()`에서 기본 생성자 순서만 맞추면 된다. 확장 필드는 Service에서 `with*()` 패턴으로 복사한다.

### 7. 문서 간 의존 관계를 시각화하면 좋다

Step 4는 5개 챕터가 서로 의존한다. 구현 순서를 틀리면 되돌리기가 번거롭다. README에 아래 같은 의존 그래프를 넣으면 도움이 된다.

```text
01 성능 측정 ──┬──→ 02 Top-K 분리
              └──→ 03 캐시
04 리뷰 도메인 ───→ 05 리뷰 RAG 통합
                         ↑
                    02, 03 완료 후
```

### 우선순위 요약

| 우선순위 | 항목 | 시점 |
| --- | --- | --- |
| **1** | `AiProperties` 설정 클래스 생성 | Step 4 시작 전 |
| **2** | 성능 로그 AOP 기반 구축 | 01 구현 시 |
| **3** | 캐시 키 정규화 기준 확정 | 03 구현 시 |
| **4** | `BookReviewSummary` 엔티티 설계 (Book과 분리) | 04 구현 시 |
| **5** | RabbitMQ 멱등성 처리 전략 | 04 구현 시 |
| **6** | `BookSearchResponse` Builder/record 리팩토링 | 05 구현 시 |
| **7** | 문서 간 의존 그래프 추가 | README 정리 시 |
