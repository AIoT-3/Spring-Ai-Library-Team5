# Step 1 코드 리뷰

이 문서는 `step-1-myself` 구현 코드와 문서를 `step-1` 강의자료와 비교하여 리뷰한 결과다.

---

## 총평

강의자료는 **이벤트 기반 아키텍처**(CSV 파싱 → Event 발행 → Listener 버퍼 → BatchService 저장)로 데이터를 적재한다. 본인 구현은 이를 **Spring Batch**(ItemReader → ItemProcessor → ItemWriter + Job/Step metadata)로 바꿨다.

이 선택은 **매우 적절하다**. 강의자료 방식의 핵심 약점(이벤트 누락 시 데이터 유실, 재시작 불가, 리스너 내부 버퍼의 트랜잭션 경계 불명확)을 Spring Batch가 구조적으로 해결한다.

검색 쪽도 강의자료의 `QBookSearchResponse` 방식 대신 `Projections.constructor()`를 쓰고, Service를 단순하게 유지한 것은 Step 1 범위에 맞는 좋은 판단이다.

다만 성능을 더 끌어올리려면 아래에서 다루는 몇 가지 포인트를 고려할 수 있다.

---

## 1. 배치 적재 (Spring Batch)

### 1-1. 강의자료와의 차이점

| 구분 | 강의자료 (step-1) | 본인 구현 (step-1-myself) |
| --- | --- | --- |
| 적재 방식 | `CsvBookParser` + `@EventListener` + `BookBatchService.saveAll()` | Spring Batch `ItemReader` → `ItemProcessor` → `ItemWriter` |
| 트랜잭션 | `@Transactional`으로 전체를 한 트랜잭션, 내부에서 subList로 분할 | chunk 단위 자동 트랜잭션 커밋 |
| 실패 복구 | 없음 (전체 롤백 또는 부분 유실) | `JobExecution` metadata 기반 재시작 가능 |
| 리소스 관리 | try-with-resources (파서 내부) | `ItemStreamReader`의 `open()`/`close()` 생명주기 |
| DTO 필드 | `publishDate` (String), 필드 적음 | `firstPublishDate` (LocalDate), `price`, `editionPublishDate` 등 확장 |

### 1-2. 잘한 점

- **`ItemStreamReader` 직접 구현**: `open()`/`read()`/`close()` 생명주기를 정확히 지켰다. 특히 `close()`에서 `csvParser` → `reader` 순서로 닫는 것이 올바르다.
- **`@StepScope` 적용**: Reader에 상태(iterator, csvParser)가 있으므로 Step 실행 단위로 생성되게 한 것은 정확한 판단이다.
- **Processor 분리**: trim + null 변환 + 필수값 검증을 Processor에서 처리하고, `null` 반환으로 필터링하는 방식은 Spring Batch 표준 패턴이다.
- **`BookRawData`에 `LocalDate`, `BigDecimal` 사용**: 강의자료는 `publishDate`를 String으로 두었는데, 본인 구현은 Reader 단계에서 이미 타입 변환을 해서 이후 단계의 부담을 줄였다.
- **날짜 파싱 방어 코드**: `replaceAll("[^0-9]", "")`로 하이픈/슬래시 등 구분자를 제거한 뒤 파싱하고, 예외 시 `null`로 처리한 것은 실제 공공 데이터의 불규칙한 날짜 형식을 잘 고려했다.

### 1-3. 개선 포인트

#### (1) Writer 저장 방식

초기 구현에서는 Writer가 비어 있었고, 이후 JPA `saveAll()` 방식과 직접 `JdbcTemplate.batchUpdate()` 방식을 검토했다. 현재 구현은 Spring Batch의 `JdbcBatchItemWriter<Book>`를 사용한다.

이 선택은 초기 도서 데이터처럼 한 번 대량 적재하는 작업에 더 적합하다. Spring Batch의 chunk, 트랜잭션, Job metadata는 유지하면서 실제 저장은 JDBC batch insert로 수행하기 때문이다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| JPA `saveAll()` | 코드가 가장 단순하고 Entity 생명주기를 탄다 | 대량 insert 성능이 상대적으로 낮다 |
| 직접 `JdbcTemplate.batchUpdate()` | JDBC batch를 가장 직접적으로 제어한다 | `PreparedStatement` 매핑 코드가 길어진다 |
| `JdbcBatchItemWriter` | JDBC batch 성능과 Spring Batch 표준 구조를 같이 가져간다 | SQL과 named parameter 매핑은 직접 관리해야 한다 |

#### (2) Reader에서 파싱 실패 시 전체 Job이 멈춘다

`parseLong()`과 `parseBigDecimal()`에서 형식이 잘못된 값이 오면 `NumberFormatException`이 발생하고, Spring Batch가 chunk 전체를 실패 처리한다.

```java
private static Long parseLong(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    return Long.parseLong(value);  // ← 형식 오류 시 예외 발생
}
```

**개선 방향**: 날짜 파싱처럼 try-catch로 감싸서 `null` 반환하거나, Step에 `.faultTolerant().skip(NumberFormatException.class).skipLimit(100)` 같은 skip 정책을 추가한다.

```java
private static Long parseLong(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    try {
        return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
        return null;
    }
}
```

#### (3) `FlatFileItemReader` 대안

본인 문서에서 `FlatFileItemReader` + `FieldSetMapper` 방식도 설명했다. 현재 CSV가 일반적인 comma-separated라면 `FlatFileItemReader`로 바꾸면:

- `open()`/`close()` 직접 관리 불필요
- 재시작 시 읽기 위치 자동 복원 (`saveState` 기본 활성)
- 코드량 감소

다만 현재 직접 구현한 Reader도 잘 동작하고, CSV 구조가 복잡해질 가능성이 있으면 유지해도 괜찮다.

#### (4) InitProperties에 `@ConfigurationProperties` 바인딩 방식

현재 `@Component` + `@ConfigurationProperties`를 같이 쓰고 있다. Spring Boot 3.x에서는 **immutable `@ConfigurationProperties`**(record 또는 `@ConstructorBinding`)를 권장한다.

```java
// 현재
@Component
@Data
@ConfigurationProperties(prefix = "init")
public class InitProperties { ... }

// 권장
@ConfigurationProperties(prefix = "init")
public record InitProperties(
        String bookFile,
        boolean enable,
        int batchSize
) {}
```

record로 바꾸면 불변이 보장되고, `@EnableConfigurationProperties(InitProperties.class)`를 Config에 추가하면 `@Component` 없이 동작한다.

---

## 2. 기본 검색 (QueryDSL)

### 2-1. 강의자료와의 차이점

| 구분 | 강의자료 | 본인 구현 |
| --- | --- | --- |
| DTO Projection | `new QBookSearchResponse(...)` | `Projections.constructor(...)` |
| 검색 대상 필드 | title, authorName만 | title, authorName, publisherName, subtitle, category, **bookContent** |
| 응답 DTO 필드 | id, isbn, title, authorName, publisherName, bookContent, category, imageUrl | + **price**, **editionPublishDate** |
| null 체크 | `StringUtils.isNotEmpty()` (Apache) | `StringUtils.hasText()` (Spring) |
| count 쿼리 | `long total = ... .fetchOne()` (NPE 가능) | `Long total = ... total == null ? 0 : total` (안전) |

### 2-2. 잘한 점

- **`Projections.constructor()` 선택**: DTO가 QueryDSL에 의존하지 않는다. `@QueryProjection`이 있기는 하지만 실제 쿼리에서는 `Projections.constructor()`를 쓰고 있어 이중 안전장치가 된다.
- **projection을 별도 메서드로 추출**: `bookSearchProjection()`을 분리해서 재사용성을 확보했다. 이후 vector search나 hybrid search에서 같은 projection을 쓸 때 코드 중복을 줄일 수 있다.
- **count null 방어**: `total == null ? 0 : total`로 NPE를 방지한 것은 강의자료보다 안전하다.
- **`StringUtils.hasText()` 사용**: `isNotEmpty()`보다 공백 문자열까지 걸러주므로 더 적합하다.
- **`request.keyword().trim()` 처리**: 사용자 입력 앞뒤 공백을 제거한 것은 실무에서 자주 빠뜨리는 부분인데 잘 처리했다.

### 2-3. 개선 포인트 (성능 중심)

#### (1) `bookContent`에 대한 `containsIgnoreCase` — 가장 큰 병목 후보

```java
.or(book.bookContent.containsIgnoreCase(keyword))
```

`bookContent`는 `TEXT` 타입 컬럼이다. `LIKE '%keyword%'`가 전체 테이블을 full scan하면서 긴 텍스트를 매번 비교한다. 데이터 10만건 기준으로 **이 한 줄이 전체 검색 시간의 80% 이상을 차지**할 수 있다.

**개선 방향 (단계별)**:

1. **즉시 적용 가능**: `bookContent`를 키워드 검색 대상에서 분리. 짧은 필드(title, authorName, publisherName, subtitle, category)만 LIKE 검색하고, 본문 검색은 별도 옵션으로 분리한다.

2. **PostgreSQL FTS 적용** (본인 문서 04에서 이미 계획):
```java
BooleanExpression ftsCondition = Expressions.booleanTemplate(
        "function('ts_match_korean', {0}, {1}) = true",
        book.bookContent,
        keyword
);
builder.and(
    book.title.containsIgnoreCase(keyword)
        .or(book.authorName.containsIgnoreCase(keyword))
        .or(ftsCondition)  // bookContent만 FTS
);
```

3. **GIN 인덱스 생성**:
```sql
CREATE INDEX idx_books_book_content_fts
ON books USING GIN (to_tsvector('simple', book_content));
```

#### (2) count 쿼리 최적화 — `fetchResults()` 대신 분리한 것은 좋지만 개선 여지 있음

현재 content 쿼리와 count 쿼리에서 `commonWhere(request)`를 두 번 호출하고 있다. `BooleanBuilder`는 불변이 아니므로 이론적으로는 안전하지만, 조건이 복잡해지면 BooleanBuilder를 한 번 만들어 재사용하는 것이 명확하다. (현재 코드가 이미 이렇게 하고 있어 좋다.)

**추가 성능 팁**: 페이지네이션에서 마지막 페이지나 결과가 pageSize보다 적을 때는 count 쿼리를 생략할 수 있다.

```java
// content.size() < pageSize이면 total = offset + content.size()
if (content.size() < pageable.getPageSize()) {
    return new PageImpl<>(content, pageable, pageable.getOffset() + content.size());
}

Long total = jpaQueryFactory
        .select(book.count())
        .from(book)
        .where(where)
        .fetchOne();
return new PageImpl<>(content, pageable, total == null ? 0 : total);
```

이 최적화는 검색 결과가 적을 때 DB 왕복을 1회 줄여준다.

#### (3) 정렬 기준 부재

현재 `search()` 메서드에 `orderBy()`가 없다. 정렬 기준이 없으면 PostgreSQL이 임의 순서로 반환하므로 페이지를 넘길 때 같은 결과가 다른 페이지에 나올 수 있다.

```java
// 권장: 기본 정렬 기준 추가
.orderBy(book.id.desc())
```

`Pageable`의 `Sort`를 동적으로 적용하려면 `OrderSpecifier`로 변환하는 유틸이 필요하다. Step 1에서는 `book.id.desc()` 기본 정렬만 넣어도 충분하다.

---

## 3. 검색 API / 서비스 계층

### 3-1. 잘한 점

- **Service를 단순하게 유지**: 강의자료처럼 전략 패턴을 미리 넣지 않은 것은 Step 1 범위에서 올바른 판단이다. YAGNI(You Aren't Gonna Need It) 원칙에 부합한다.
- **`@Transactional(readOnly = true)` 적용**: 읽기 전용 트랜잭션으로 Hibernate flush 생략, dirty checking 비활성화 등 성능 이점이 있다.
- **Controller에서 `BookRepository`를 직접 주입**: 상세 조회(`/books/{id}`)는 단순 ID 조회이므로 Service를 거치지 않고 Repository를 직접 쓴 것은 과도한 추상화를 피한 실용적 판단이다.

### 3-2. 개선 포인트

#### (1) `BookSearchRequest`의 compact constructor에서 `searchType` 기본값

```java
public BookSearchRequest {
    if (searchType == null) {
        searchType = SearchType.KEYWORD;
    }
}
```

이 부분은 문서에서 강의자료 기본값(`RAG`)과 다르게 `KEYWORD`로 설정한 것이 Step 1에 적합하다. 다만 이후 Step 2+에서 기본 검색 타입을 바꿀 때 **이 compact constructor가 영향받지 않도록** `SearchType.from()` 메서드와 일관성을 유지해야 한다.

#### (2) Controller에서 상세 조회 예외 처리

```java
Book book = bookRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Book not found. id=" + id));
```

`IllegalArgumentException`은 HTTP 500으로 매핑된다. 사용자 경험상 **404 Not Found**가 더 적합하다. `@ResponseStatus(HttpStatus.NOT_FOUND)` 커스텀 예외를 만들거나, `@ExceptionHandler`를 추가하는 것을 권장한다.

#### (3) `BookRepository.findById()` 재정의

```java
@Query("SELECT b FROM Book b WHERE b.id = :id")
Optional<Book> findById(@Param("id") long id);
```

`JpaRepository<Book, Long>`이 이미 `findById(Long id)`를 제공한다. JPQL로 재정의할 필요가 없다. 오히려 `long` (primitive) vs `Long` (wrapper) 차이로 오토박싱이 발생하고, `SimpleJpaRepository`의 `em.find()` 최적화(1차 캐시 활용)를 못 쓰게 된다.

**권장**: 이 메서드를 삭제하고 기본 `findById(Long id)`를 사용한다.

#### (4) `BookRepositoryCustom`의 `@NoRepositoryBean`

```java
@NoRepositoryBean
public interface BookRepositoryCustom { ... }
```

커스텀 Repository 인터페이스에 `@NoRepositoryBean`은 불필요하다. 이 어노테이션은 Spring Data가 자동으로 구현체를 만들지 않도록 하는 것인데, `BookRepositoryCustom`은 `JpaRepository`를 extend하지 않으므로 Spring Data가 구현체를 만들 시도를 하지 않는다. 제거해도 동작에 영향 없다.

---

## 4. 성능 관점 종합 권장사항

우선순위 순으로 정리한다.

| 우선순위 | 항목 | 예상 효과 | 난이도 |
| --- | --- | --- | --- |
| **1** | `bookContent` LIKE 검색 분리 또는 FTS 전환 | 검색 응답시간 50~80% 감소 | 중 |
| **2** | 기본 정렬(`orderBy`) 추가 | 페이징 결과 안정성 확보 | 하 |
| **3** | Writer를 `JdbcBatchItemWriter`로 유지 | 초기 CSV 대량 적재 성능 확보 | 하 |
| **4** | Reader 파싱 실패 방어 (try-catch) | Job 안정성 향상 | 하 |
| **5** | count 쿼리 생략 최적화 | 검색 쿼리 DB 왕복 1회 감소 | 하 |
| **6** | `findById()` 재정의 제거 | 1차 캐시 활용, 오토박싱 제거 | 하 |
| **7** | PostgreSQL JDBC `rewriteBatchedInserts=true` 검토 | 실제 PostgreSQL batch insert 성능 향상 | 하 |
| **8** | `InitProperties` record 변환 | 불변 보장, 코드 간결화 | 하 |

---

## 5. 문서 품질 리뷰

### 잘한 점

- **4개 문서 구성**: 배치, 검색, API, 최적화를 별도 문서로 분리한 것은 읽기 좋다.
- **강의자료 참조를 명시**: 각 문서가 어느 강의자료를 기반으로 하는지 첫 줄에 명시했다.
- **코드 예시가 실제 프로젝트와 일치**: 문서의 코드가 실제 소스 코드와 거의 동일하다. 문서와 코드가 따로 놀지 않는다.
- **선택지를 비교하는 표**: `ItemStreamReader` vs `FlatFileItemReader`, `QBookSearchResponse` vs `Projections.constructor()` 같은 비교표가 학습에 좋다.

### 개선 포인트

- **03 문서에 Strategy 패턴 언급**: "아직은 검색 전략 클래스를 따로 만들 필요가 없다"고 한 것은 좋은데, 02 문서에서는 `KeywordSearchStrategy`를 예시로 보여주고 있다. 실제 코드에는 Strategy가 없으므로 02 문서의 해당 섹션은 "이후 확장 방향" 정도로 톤을 조정하면 혼동이 줄어든다.
- **04 문서의 범위**: 현재 04 문서는 "이런 게 있다"는 소개 수준이다. 실제 적용 코드 예시(QueryDSL에서 FTS를 어떻게 쓰는지)를 더 구체적으로 보여주면 Step 2로 넘어갈 때 도움이 된다.

---

## 6. 결론

강의자료의 이벤트 기반 CSV 적재를 Spring Batch로 바꾼 것은 **성능과 안정성 모두에서 올바른 선택**이다. 검색 구현도 계층 분리가 깔끔하고 QueryDSL 사용이 적절하다.

가장 빠르게 체감할 수 있는 성능 개선은:

1. **`bookContent` LIKE 검색을 FTS로 전환**하거나 검색 대상에서 분리
2. **`orderBy()` 추가**로 페이징 안정성 확보
3. **`JdbcBatchItemWriter` + 적절한 chunk size**로 초기 CSV 적재 성능 확보

이 세 가지만 적용해도 Step 1의 완성도가 크게 올라간다.
