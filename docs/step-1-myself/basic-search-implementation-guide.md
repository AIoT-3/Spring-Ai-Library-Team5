# 기본 도서 검색을 QueryDSL로 구현하는 방법

이 문서는 `docs/step-1/02.basic-search-implementation.md` 내용을 현재 프로젝트에 적용하기 위한 구현 가이드다.

목표는 도서 CSV로 적재된 `books` 테이블을 대상으로 제목, 저자, 출판사, 부제, 카테고리, ISBN 기준 검색을 제공하는 것이다. 이 단계에서는 벡터 검색, 하이브리드 검색, RAG 검색이 아니라 가장 기본이 되는 키워드 검색 흐름을 먼저 만든다.

## 왜 Repository에 검색 로직을 분리하는가

검색은 단순히 `findAll()`을 호출하는 기능이 아니다. 사용자가 입력한 조건에 따라 `WHERE` 절이 달라지고, 결과는 화면이나 API에 필요한 형태로 가공되어야 한다.

예를 들어 아래 요청들은 서로 다른 쿼리를 만들어야 한다.

```text
keyword=자바
isbn=978...
keyword=자바&isbn=978...
조건 없음
```

이런 동적 조건을 Controller나 Service에 직접 넣으면 계층 책임이 흐려진다.

- Controller는 HTTP 요청과 응답 처리에 집중한다.
- Service는 검색 전략 선택과 비즈니스 흐름을 담당한다.
- Repository는 DB 조회 쿼리를 담당한다.
- DTO는 Entity를 외부에 직접 노출하지 않도록 검색 결과 모양을 정한다.

현재 프로젝트에서는 이 책임 분리를 아래 구조로 가져간다.

```text
BookSearchController
  -> BookSearchService
      -> KeywordSearchStrategy
          -> BookRepository.search(...)
              -> BookRepositoryImpl QueryDSL 동적 쿼리
```

## 구현 대상 파일

기본 검색을 구현할 때 필요한 핵심 파일은 아래와 같다.

| 파일 | 역할 |
| --- | --- |
| `BookSearchRequest` | 검색 요청 조건 DTO |
| `BookSearchResponse` | 검색 결과 응답 DTO |
| `BookRepository` | Spring Data JPA 기본 Repository |
| `BookRepositoryCustom` | QueryDSL 커스텀 검색 메서드 선언 |
| `BookRepositoryImpl` | QueryDSL 검색 쿼리 구현 |
| `QuerydslConfig` | `JPAQueryFactory` Bean 등록 |
| `KeywordSearchStrategy` | 키워드 검색 전략 |
| `BookSearchService` | 검색 타입에 맞는 전략 선택 |

이 문서는 기본 검색 구현에 초점을 둔다. 이후 단계의 벡터 검색, 하이브리드 검색, RAG 검색까지 한 번에 넣으면 첫 검색 기능의 책임이 흐려진다.

## QueryDSL 의존성과 Q 클래스 생성

현재 프로젝트는 `pom.xml`에 QueryDSL 의존성이 들어가 있다.

```xml
<dependency>
    <groupId>com.querydsl</groupId>
    <artifactId>querydsl-apt</artifactId>
    <version>5.1.0</version>
    <classifier>jakarta</classifier>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>com.querydsl</groupId>
    <artifactId>querydsl-jpa</artifactId>
    <version>5.1.0</version>
    <classifier>jakarta</classifier>
    <scope>compile</scope>
</dependency>
```

QueryDSL을 쓰려면 `QBook` 같은 Q 타입이 생성되어야 한다. 보통 Maven 빌드를 돌리면 annotation processor가 동작한다.

```bash
./mvnw clean compile
```

만약 `QBook`을 찾을 수 없다는 컴파일 오류가 나면 먼저 빌드가 정상적으로 Q 타입을 생성하는지 확인한다. IntelliJ에서는 annotation processing이 꺼져 있으면 IDE 안에서만 빨간 줄이 보일 수 있다.

## `JPAQueryFactory` 설정

QueryDSL 쿼리를 만들기 위해 `JPAQueryFactory`를 Spring Bean으로 등록한다.

```java
package com.nhnacademy.library.core.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
```

이 설정이 있으면 Repository 구현체에서 생성자 주입으로 `JPAQueryFactory`를 받을 수 있다.

## 요청 DTO 만들기

검색 요청은 `BookSearchRequest`로 받는다.

```java
package com.nhnacademy.library.core.book.dto;

import com.nhnacademy.library.core.book.domain.SearchType;
import jakarta.validation.constraints.Size;

public record BookSearchRequest(
        @Size(max = 100)
        String keyword,

        @Size(max = 20)
        String isbn,

        SearchType searchType,
        float[] vector,
        Boolean isWarmUp
) {
    public BookSearchRequest {
        if (searchType == null) {
            searchType = SearchType.KEYWORD;
        }
        if (isWarmUp == null) {
            isWarmUp = false;
        }
    }

    public BookSearchRequest(String keyword, String isbn, SearchType searchType, float[] vector) {
        this(keyword, isbn, searchType, vector, false);
    }
}
```

기본 검색만 구현하는 단계라면 `searchType`의 기본값은 `KEYWORD`가 가장 이해하기 쉽다. 현재 프로젝트처럼 이후 RAG 검색을 기본값으로 쓰고 싶다면 `SearchType.RAG`로 둘 수 있지만, Step 1-2의 목표와는 조금 다르다.

## 응답 DTO 만들기

검색 결과는 Entity를 그대로 반환하지 않고 `BookSearchResponse`로 반환한다.

```java
package com.nhnacademy.library.core.book.dto;

import com.nhnacademy.library.core.book.domain.Book;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class BookSearchResponse {

    private Long id;
    private String isbn;
    private String title;
    private String authorName;
    private String publisherName;
    private BigDecimal price;
    private LocalDate editionPublishDate;
    private String imageUrl;
    private String bookContent;
    private String category;

    @QueryProjection
    public BookSearchResponse(
            Long id,
            String isbn,
            String title,
            String authorName,
            String publisherName,
            BigDecimal price,
            LocalDate editionPublishDate,
            String imageUrl,
            String bookContent,
            String category
    ) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.price = price;
        this.editionPublishDate = editionPublishDate;
        this.imageUrl = imageUrl;
        this.bookContent = bookContent;
        this.category = category;
    }

    public static BookSearchResponse from(Book book) {
        return new BookSearchResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthorName(),
                book.getPublisherName(),
                book.getPrice(),
                book.getEditionPublishDate(),
                book.getImageUrl(),
                book.getBookContent(),
                book.getCategory()
        );
    }
}
```

`@QueryProjection`을 쓰면 `QBookSearchResponse`가 생성되어 타입 안정적인 DTO projection을 할 수 있다. 다만 지금 프로젝트처럼 `Projections.constructor(...)`를 쓰면 `QBookSearchResponse` 없이도 동작한다.

선택 기준은 아래와 같다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| `new QBookSearchResponse(...)` | 생성자 타입을 컴파일 타임에 검증 | DTO가 QueryDSL에 의존 |
| `Projections.constructor(...)` | DTO에 QueryDSL 의존을 줄일 수 있음 | 생성자 순서가 틀리면 런타임 문제 가능 |

학습 단계에서는 `QBookSearchResponse`가 QueryDSL 이해에 도움이 된다. 실무 코드에서는 DTO가 QueryDSL annotation에 묶이는 것을 싫어해 `Projections.constructor`를 선택하기도 한다.

## Repository 인터페이스

기본 Repository는 Spring Data JPA 기능을 받고, 검색은 커스텀 인터페이스에 위임한다.

```java
package com.nhnacademy.library.core.book.repository;

import com.nhnacademy.library.core.book.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long>, BookRepositoryCustom {
}
```

커스텀 검색 메서드는 별도 인터페이스에 둔다.

```java
package com.nhnacademy.library.core.book.repository;

import com.nhnacademy.library.core.book.dto.BookSearchRequest;
import com.nhnacademy.library.core.book.dto.BookSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BookRepositoryCustom {

    Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request);
}
```

중요한 점은 구현체 이름이다. Spring Data JPA는 기본적으로 `BookRepositoryCustom`의 구현체를 `BookRepositoryImpl` 이름으로 찾는다. 현재 프로젝트도 `BookRepositoryImpl`을 사용한다.

## QueryDSL 구현체

기본 검색 구현체는 아래처럼 작성한다.

```java
package com.nhnacademy.library.core.book.repository.impl;

import com.nhnacademy.library.core.book.domain.QBook;
import com.nhnacademy.library.core.book.dto.BookSearchRequest;
import com.nhnacademy.library.core.book.dto.BookSearchResponse;
import com.nhnacademy.library.core.book.repository.BookRepositoryCustom;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookRepositoryImpl implements BookRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QBook book = QBook.book;

    @Override
    public Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request) {
        BooleanBuilder where = commonWhere(request);

        List<BookSearchResponse> content = queryFactory
                .select(Projections.constructor(
                        BookSearchResponse.class,
                        book.id,
                        book.isbn,
                        book.title,
                        book.authorName,
                        book.publisherName,
                        book.price,
                        book.editionPublishDate,
                        book.imageUrl,
                        book.bookContent,
                        book.category
                ))
                .from(book)
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(book.count())
                .from(book)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder commonWhere(BookSearchRequest request) {
        BooleanBuilder builder = new BooleanBuilder();

        if (request == null) {
            return builder;
        }

        if (StringUtils.isNotBlank(request.keyword())) {
            String keyword = request.keyword().trim();

            builder.and(
                    book.title.containsIgnoreCase(keyword)
                            .or(book.authorName.containsIgnoreCase(keyword))
                            .or(book.publisherName.containsIgnoreCase(keyword))
                            .or(book.subtitle.containsIgnoreCase(keyword))
                            .or(book.category.containsIgnoreCase(keyword))
            );
        }

        if (StringUtils.isNotBlank(request.isbn())) {
            builder.and(book.isbn.eq(request.isbn().trim()));
        }

        return builder;
    }
}
```

이 구현의 핵심은 `commonWhere()`다. 검색어가 있으면 여러 텍스트 컬럼에 `LIKE` 조건을 걸고, ISBN이 있으면 정확히 일치하는 조건을 추가한다.

실제로 만들어지는 쿼리는 개념적으로 아래와 비슷하다.

```sql
select ...
from books
where (
    lower(title) like '%자바%'
    or lower(author_name) like '%자바%'
    or lower(publisher_name) like '%자바%'
    or lower(subtitle) like '%자바%'
    or lower(category) like '%자바%'
)
and isbn = '978...'
limit 10 offset 0
```

## `BooleanBuilder` 사용 기준

`BooleanBuilder`는 조건이 있을 때만 `where` 절에 추가하기 좋다.

```java
BooleanBuilder builder = new BooleanBuilder();

if (keyword exists) {
    builder.and(keyword condition);
}

if (isbn exists) {
    builder.and(isbn condition);
}
```

검색 조건이 늘어날 가능성이 있으면 `BooleanBuilder`가 단순하고 읽기 쉽다. 조건이 아주 많아지면 아래처럼 메서드를 나눌 수 있다.

```java
private BooleanExpression keywordContains(String keyword) {
    if (StringUtils.isBlank(keyword)) {
        return null;
    }

    return book.title.containsIgnoreCase(keyword)
            .or(book.authorName.containsIgnoreCase(keyword))
            .or(book.publisherName.containsIgnoreCase(keyword));
}
```

QueryDSL은 `where()` 안에 `null` 조건이 들어오면 무시한다. 그래서 조건별 메서드를 만드는 방식도 많이 사용한다.

## Service와 Strategy 연결

기본 검색만 구현한다면 Service에서 바로 Repository를 호출해도 된다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookSearchService {

    private final BookRepository bookRepository;

    public Page<BookSearchResponse> searchBooks(Pageable pageable, BookSearchRequest request) {
        return bookRepository.search(pageable, request);
    }
}
```

하지만 현재 프로젝트는 검색 방식이 계속 늘어나는 구조다.

```text
KEYWORD
VECTOR
HYBRID
RAG
```

그래서 `SearchStrategy`를 두고, `KeywordSearchStrategy`가 기본 검색을 담당하는 방식이 더 잘 맞는다.

```java
@Component
@RequiredArgsConstructor
public class KeywordSearchStrategy implements SearchStrategy {

    private final BookRepository bookRepository;

    @Override
    public BookSearchResult search(Pageable pageable, BookSearchRequest request) {
        if (!StringUtils.hasText(request.keyword()) && !StringUtils.hasText(request.isbn())) {
            Page<BookSearchResponse> results = bookRepository.findAll(pageable)
                    .map(BookSearchResponse::from);

            return BookSearchResult.builder()
                    .books(results)
                    .build();
        }

        return BookSearchResult.builder()
                .books(bookRepository.search(pageable, request))
                .build();
    }
}
```

조건이 없을 때 전체 목록을 보여줄지, 빈 결과를 보여줄지는 정책이다. 도서 검색 화면이라면 전체 목록을 보여주는 것도 자연스럽지만, API라면 빈 검색어를 막는 편이 나을 수 있다.

## 페이징 처리

검색 API나 화면에서는 전체 결과를 한 번에 반환하지 않고 `Pageable`을 받는다.

```java
PageRequest.of(0, 10)
```

QueryDSL에서는 이 값을 `offset`, `limit`으로 반영한다.

```java
.offset(pageable.getOffset())
.limit(pageable.getPageSize())
```

그리고 `PageImpl`을 만들기 위해 전체 개수 쿼리도 별도로 실행한다.

```java
Long total = queryFactory
        .select(book.count())
        .from(book)
        .where(where)
        .fetchOne();

return new PageImpl<>(content, pageable, total == null ? 0 : total);
```

처음 구현할 때 흔한 실수는 content 조회만 하고 total count를 실제 전체 개수가 아니라 `content.size()`로 넣는 것이다. 그렇게 하면 첫 페이지는 그럴듯하게 보이지만 페이지 네비게이션이 깨진다.

## LIKE 검색의 한계

이 단계의 검색은 `containsIgnoreCase` 기반이다. PostgreSQL 기준으로는 대략 `lower(column) like '%keyword%'` 형태가 된다.

장점은 구현이 쉽고 이해하기 좋다는 것이다.

하지만 한계도 분명하다.

- 앞에 `%`가 붙는 검색은 일반 B-tree index를 잘 활용하기 어렵다.
- 띄어쓰기, 조사, 오타, 유의어 검색에 약하다.
- 데이터가 많아질수록 느려진다.
- 제목과 내용의 중요도를 다르게 주기 어렵다.

그래서 이후 단계에서 full-text search, pgvector, hybrid search, RAG 같은 방식으로 확장한다. 기본 검색은 그 전에 만드는 가장 단순하고 안정적인 fallback 검색이다.

## 테스트 방향

Repository 검색은 가능하면 `@DataJpaTest`로 확인한다.

테스트에서 확인할 최소 케이스는 아래와 같다.

- 키워드가 제목에 포함되면 검색된다.
- 키워드가 저자명에 포함되면 검색된다.
- 키워드가 출판사명에 포함되면 검색된다.
- ISBN은 정확히 일치해야 검색된다.
- 키워드와 ISBN을 같이 주면 두 조건이 모두 적용된다.
- 조건이 없을 때 정책대로 전체 조회 또는 빈 결과를 반환한다.
- 페이징 size가 적용된다.

예시는 아래와 같다.

```java
@DataJpaTest
@Import(QuerydslConfig.class)
class BookRepositorySearchTest {

    @Autowired
    BookRepository bookRepository;

    @Test
    void searchByKeywordInTitle() {
        bookRepository.save(new Book(
                "9781111111111",
                "자바의 정석",
                "남궁성",
                "도우출판",
                null,
                null,
                null,
                "자바 입문서",
                "000",
                null,
                null
        ));

        BookSearchRequest request = new BookSearchRequest(
                "자바",
                null,
                SearchType.KEYWORD,
                null
        );

        Page<BookSearchResponse> result = bookRepository.search(PageRequest.of(0, 10), request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).contains("자바");
    }
}
```

테스트 DB가 H2라면 PostgreSQL 전용 함수나 vector 컬럼 때문에 실패할 수 있다. 기본 검색 테스트에서는 vector 검색이나 PostgreSQL 전용 full-text search 조건을 넣지 않는 편이 좋다. PostgreSQL 전용 기능까지 검증하려면 Testcontainers로 PostgreSQL을 띄우는 쪽이 더 정확하다.

## SQL 로그 확인

구현 후 실제 SQL을 확인하고 싶으면 로컬 프로필에 아래 설정을 켠다.

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=debug
logging.level.org.hibernate.orm.jdbc.bind=trace
```

공용 `application.properties`에 계속 켜두면 로그가 너무 많아질 수 있으므로 `application-dev.properties`나 테스트 설정에만 두는 편이 낫다.

## 구현 순서 추천

처음부터 Controller까지 한 번에 붙이지 말고 아래 순서로 확인하는 것이 좋다.

1. `QuerydslConfig`를 만든다.
2. `BookSearchRequest`, `BookSearchResponse`를 만든다.
3. `BookRepositoryCustom.search()`를 선언한다.
4. `BookRepositoryImpl.search()`를 QueryDSL로 구현한다.
5. Repository 테스트로 키워드, ISBN, 페이징을 검증한다.
6. `KeywordSearchStrategy`에서 Repository를 호출한다.
7. `BookSearchService`가 `KEYWORD` 요청을 `KeywordSearchStrategy`로 보내게 한다.
8. Controller나 화면에서 `keyword`, `isbn`, `page`, `size`를 연결한다.

이 순서로 가면 검색 쿼리 문제와 화면 문제를 분리해서 볼 수 있다.

## 현재 프로젝트에서 주의할 점

현재 프로젝트의 `BookSearchRequest` 기본 `searchType`은 `RAG`다. 그래서 단순 키워드 검색을 테스트하려면 명시적으로 `SearchType.KEYWORD`를 넣어야 한다.

```java
new BookSearchRequest("자바", null, SearchType.KEYWORD, null)
```

그렇지 않으면 `BookSearchService`가 embedding 생성, hybrid, RAG 흐름으로 들어갈 수 있다. Step 1-2의 기본 검색 구현을 확인할 때는 반드시 `KEYWORD`로 고정해서 테스트하는 것이 좋다.

또 현재 `BookRepositoryImpl`에는 이후 단계의 리뷰 요약 조합, 벡터 검색, PostgreSQL 함수 호출이 이미 섞여 있다. 학습 순서대로 다시 구현한다면 처음에는 아래까지만 두는 편이 좋다.

- `search(Pageable, BookSearchRequest)`
- `commonWhere(BookSearchRequest)`
- 키워드 LIKE 조건
- ISBN 정확 일치 조건
- count query

이후 단계에서 리뷰 정보 조합, vector search, hybrid search를 차례로 추가하면 변경 이유가 훨씬 선명해진다.

## 완료 기준

기본 검색 구현이 끝났다고 볼 수 있는 기준은 아래와 같다.

- `keyword`로 제목, 저자, 출판사, 부제, 카테고리를 검색할 수 있다.
- `isbn`으로 정확 일치 검색을 할 수 있다.
- `keyword`와 `isbn`을 함께 주면 AND 조건으로 동작한다.
- 결과가 `BookSearchResponse`로 반환된다.
- `Pageable`의 page, size가 적용된다.
- total count가 실제 전체 검색 결과 수로 계산된다.
- 조건 없는 요청의 정책이 정해져 있다.
- Repository 테스트가 최소 3개 이상 있다.

이 단계가 안정적으로 끝나면 다음 단계에서 Controller와 Thymeleaf 화면을 연결하면 된다.
