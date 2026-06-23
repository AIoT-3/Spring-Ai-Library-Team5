# 04. 현재 프로젝트 검색 성능 정리

이 문서는 강의자료 `docs/step-1/04.search-optimization.md`를 현재 프로젝트 기준으로 적용하는 방법이다.

핵심은 처음부터 최적화를 많이 넣는 것이 아니라, 어떤 검색이 느려지는지 구분할 수 있게 단계적으로 바꾸는 것이다.

## 현재 검색 구조

현재 기본 검색은 `BookRepositoryImpl`에서 QueryDSL로 처리한다.

```text
BookSearchController
  -> BookSearchService
      -> BookRepository.search(...)
          -> BookRepositoryImpl
```

검색 대상은 아래 필드다.

```text
title
authorName
publisherName
subtitle
category
bookContent
isbn
```

현재 구현은 짧은 텍스트와 긴 본문을 모두 `containsIgnoreCase`로 검색한다. SQL로 보면 대부분 `LIKE '%keyword%'`에 가깝다.

## 1단계: 기본 검색을 먼저 안정화

먼저 아래가 정상인지 확인한다.

| 확인 항목 | 이유 |
| --- | --- |
| `BookRepositoryImplTest` 통과 | QueryDSL projection과 조건식 검증 |
| 검색어 없는 경우 전체 목록 조회 | 첫 화면에서 결과가 보여야 함 |
| `keyword=자바` 검색 | 기본 키워드 검색 확인 |
| `isbn=978...` 검색 | 정확 일치 조건 확인 |
| 페이지 크기와 total count | 화면 페이징 오류 방지 |

이 단계에서는 전문 검색이나 vector 검색을 넣지 않는다. 기본 검색이 흔들리면 이후 최적화가 맞는지 판단하기 어렵다.

## 2단계: H2 테스트와 PostgreSQL 운영 설정 분리

현재 Entity에는 pgvector를 위한 `embedding` 컬럼이 있다.

```java
@Column(name = "embedding", columnDefinition = "vector")
private float[] embedding;
```

PostgreSQL에서는 `vector` 타입을 pgvector 확장이 처리한다. H2에는 이 타입이 없기 때문에 테스트 프로필에서 임시 domain을 만든다.

```properties
spring.datasource.url=jdbc:h2:mem:book_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARBINARY
```

이 설정은 `src/test/resources/application-test.properties`에 둔다. 테스트 클래스에는 아래처럼만 쓴다.

```java
@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class BookRepositoryImplTest {
}
```

이렇게 하면 “H2를 쓸 때 필요한 설정”과 “테스트 코드 자체”가 섞이지 않는다.

## 3단계: 인덱스가 도움 되는 검색과 아닌 검색 구분

인덱스는 모든 `LIKE` 검색을 빠르게 해주지 않는다.

| 검색 방식 | 일반 B-Tree 인덱스 효과 |
| --- | --- |
| `isbn = ?` | 좋음 |
| `title LIKE '자바%'` | 조건에 따라 도움 됨 |
| `title LIKE '%자바%'` | 거의 도움 안 됨 |
| `book_content LIKE '%자바%'` | 데이터가 많아질수록 느림 |

`isbn`은 정확 일치 검색이므로 unique index가 있으면 충분하다. 반면 `containsIgnoreCase`는 부분 일치 검색이라 일반 인덱스 효과를 기대하기 어렵다.

## 4단계: 긴 본문은 전문 검색 후보

가장 먼저 느려질 가능성이 큰 필드는 `bookContent`다. 제목, 저자, 출판사는 짧지만 본문은 길기 때문이다.

PostgreSQL에서는 full-text search를 쓸 수 있다.

```sql
CREATE INDEX idx_books_book_content_fts
ON books
USING GIN (to_tsvector('simple', book_content));
```

조회 조건은 이런 형태가 된다.

```sql
WHERE to_tsvector('simple', book_content)
      @@ plainto_tsquery('simple', :keyword)
```

`simple`은 한글 형태소 분석을 정교하게 하지는 않지만, 별도 확장 설치 없이 시작할 수 있다. 운영 DB에서 한글 검색 품질이 중요해지면 그때 형태소 분석 확장을 검토한다.

## 5단계: QueryDSL에서 PostgreSQL 함수를 쓰는 경우

현재 프로젝트에는 PostgreSQL 함수를 QueryDSL/Hibernate에서 쓰기 위한 설정이 있다.

```text
src/main/java/com/nhnacademy/ailibrarymyself/core/config/PostgreSQLFunctionContributor.java
src/main/resources/META-INF/services/org.hibernate.boot.model.FunctionContributor
```

이 설정은 PostgreSQL 전용 full-text search 조건을 QueryDSL에 붙일 때 필요하다.

예시는 이런 형태다.

```java
Expressions.booleanTemplate(
        "fts_match({0}, {1})",
        book.bookContent,
        keyword
)
```

다만 이 조건은 H2 테스트에서 검증하기 어렵다. H2는 PostgreSQL의 `to_tsvector`, `plainto_tsquery`, GIN index를 실제로 제공하지 않는다.

## 테스트 전략

검색 테스트는 두 종류로 나누는 것이 좋다.

| 테스트 종류 | DB | 검증 대상 |
| --- | --- | --- |
| 기본 Repository 테스트 | H2 + `test` profile | QueryDSL 조건, DTO projection, 페이징 |
| PostgreSQL 기능 테스트 | Testcontainers PostgreSQL | full-text search, pgvector, 실제 index |

지금 단계에서는 H2 테스트만으로 충분하다. full-text search나 vector search를 구현하기 시작하면 Testcontainers를 추가하는 쪽이 정확하다.

## 지금 바로 적용할 기준

현재 프로젝트에서는 아래 순서가 가장 덜 헷갈린다.

```text
1. BookRepositoryImpl 기본 LIKE 검색 유지
2. H2 테스트 설정은 application-test.properties로 이동
3. 화면과 페이징이 정상 동작하는지 확인
4. 데이터가 많아져 bookContent 검색이 느려질 때 PostgreSQL FTS 추가
5. FTS부터는 H2가 아니라 PostgreSQL 기반 테스트로 검증
```

성능 최적화는 “코드를 더 많이 넣는 것”보다 “느린 지점을 정확히 분리하는 것”이 중요하다. 지금은 구조를 단순하게 유지하고, PostgreSQL 전용 기능은 필요해지는 시점에 별도 테스트와 함께 넣는 편이 좋다.
