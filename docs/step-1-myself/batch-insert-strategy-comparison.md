# 배치 적재 방식 비교

이 문서는 도서 CSV 초기 적재 방식을 단계별로 비교한다.

현재 결론은 **Spring Batch 구조는 유지하고, 저장 Writer만 `JdbcBatchItemWriter`로 가져가는 것**이다.

## 결론

초기 도서 데이터는 한 번 대량으로 넣는 성격이 강하다. 그래서 JPA Entity 생명주기보다 insert 성능이 더 중요하다.

```text
Reader / Processor / Step / Job metadata
  -> Spring Batch 사용

실제 INSERT
  -> JdbcBatchItemWriter 사용
```

이렇게 하면 Spring Batch의 장점과 JDBC batch insert의 성능을 같이 가져갈 수 있다.

## 방식별 비교

| 단계 | 방식 | 장점 | 단점 | 현재 판단 |
| --- | --- | --- | --- | --- |
| 1 | Plain JPA `saveAll()` | Entity 중심 코드가 가장 단순함 | 대량 insert에서는 영속성 컨텍스트와 Entity lifecycle 비용이 큼 | 기준선으로 비교 |
| 2 | Spring Batch + JPA `saveAll()` | Spring Batch의 Reader/Processor/Chunk 구조를 타면서 JPA로 저장 | 저장 자체는 JPA 비용이 남음 | Spring Batch + JPA Writer 비교 |
| 3 | Spring Batch + 직접 `JdbcTemplate.batchUpdate()` | Spring Batch 구조 안에서 JDBC batch를 직접 제어 | `PreparedStatement` 매핑 코드가 길고 유지보수 부담이 큼 | 성능은 좋지만 코드가 과함 |
| 4 | Spring Batch + `JdbcBatchItemWriter` | Spring Batch 표준 Writer, JDBC batch 성능, 코드 간결성의 균형이 좋음 | SQL과 named parameter 매핑은 직접 관리해야 함 | 현재 프로젝트 추천 |

## 1. 이벤트 리스너 방식에서 단순 청크 처리로 변경

처음 이벤트 리스너 방식은 대략 이런 흐름이었다.

```text
CSV Parser
  -> BookParsedEvent 발행
      -> EventListener가 버퍼에 저장
          -> 일정 개수마다 저장 서비스 호출
  -> BookParsingCompleteEvent로 마지막 flush
```

이 방식의 문제는 저장 흐름이 Spring Batch처럼 명확한 Job/Step 단위가 아니라는 점이다.

- 실패한 지점부터 재시작하기 어렵다.
- 이벤트 리스너 내부 버퍼의 트랜잭션 경계가 애매하다.
- 마지막 flush 이벤트를 놓치면 남은 데이터가 저장되지 않을 수 있다.
- 실행 이력, 성공/실패 상태, 처리 건수 추적을 직접 만들어야 한다.

그래서 초기 데이터 대량 적재에는 Spring Batch가 더 적합하다.

현재 비교에서는 삭제된 이벤트 리스너 코드를 다시 사용하지 않는다. 대신 `step-1-code-review-notes.md`에서 제안한 것처럼 이벤트 없이 명시적으로 청크를 모아 저장하는 방식을 비교 대상으로 둔다.

```text
CSV Reader
  -> chunk 리스트에 담음
  -> chunkSize가 되면 bookRepository.saveAll(...)
  -> 마지막 남은 chunk 저장
```

이 방식은 이벤트 리스너의 대체안이지, 최종 선택안은 아니다.

## 2. JPA saveAll

가장 단순한 구조는 같은 `Book` 테스트 데이터 N건을 한 번에 `saveAll()`로 저장하는 방식이다.

```java
bookRepository.saveAll(books);
bookRepository.flush();
entityManager.clear();
```

코드는 가장 짧지만, 대량 insert에서는 JPA의 영속성 컨텍스트와 Entity lifecycle 비용이 들어간다.

Spring Batch + JPA `saveAll()`은 이 방식과 저장 호출은 비슷하지만 같지는 않다. Spring Batch를 쓰면 Reader, Processor, Writer, chunk transaction, Job/Step 실행 흐름을 타고, Writer 내부에서 `saveAll()`을 호출한다.

## 3. Spring Batch + 직접 JdbcTemplate.batchUpdate

직접 구현하면 이런 형태가 된다.

```text
Spring Batch chunk
  -> jdbcTemplate.batchUpdate(...)
```

성능은 좋다. 하지만 JPA가 대신 해주던 일을 직접 해야 한다.

```java
jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
    @Override
    public void setValues(PreparedStatement ps, int i) throws SQLException {
        Book book = books.get(i);
        ps.setString(1, book.getIsbn());
        ps.setString(2, book.getTitle());
        // ...
    }
});
```

- 컬럼 순서 관리
- `PreparedStatement` 파라미터 번호 관리
- `LocalDate` 변환
- null 처리
- id sequence 처리
- created_at 처리

그래서 코드가 갑자기 길어진다. 성능은 좋지만 현재 프로젝트의 학습/유지보수 관점에서는 부담이 크다.

## 4. 현재 선택: JdbcBatchItemWriter

현재 구조는 아래와 같다.

```text
ItemReader<BookRawData>
  -> ItemProcessor<BookRawData, Book>
      -> JdbcBatchItemWriter<Book>
```

`JdbcBatchItemWriter`는 Spring Batch가 제공하는 JDBC batch writer다.

```java
@Bean
public JdbcBatchItemWriter<Book> bookRawDataWriter(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<Book>()
            .dataSource(dataSource)
            .sql(BOOK_INSERT_SQL)
            .beanMapped()
            .build();
}
```

SQL은 named parameter를 쓴다.

```sql
INSERT INTO books (
    id, isbn, title, author_name, publisher_name,
    first_publish_date, price, image_url, book_content,
    category, subtitle, edition_publish_date, created_at
) VALUES (
    nextval('public.book_sequence'),
    :isbn, :title, :authorName, :publisherName,
    :firstPublishDate, :price, :imageUrl, :bookContent,
    :category, :subtitle, :editionPublishDate,
    CURRENT_TIMESTAMP
)
```

`beanMapped()`를 쓰면 `:isbn`은 `Book#getIsbn()`, `:publisherName`은 `Book#getPublisherName()`과 매핑된다.

## 성능 검증 테스트

성능 검증 테스트는 두 종류로 나눈다.

첫 번째는 Writer 단독 비교다. CSV 파싱 비용을 제외하고, 이미 `Book` 객체가 준비되어 있다는 조건에서 저장 방식만 비교한다.

두 번째는 CSV 파싱 포함 비교다. 실제 CSV 파일을 `CsvBookRawDataReader`로 읽고, `BookRawDataProcessor`로 `Book`으로 변환한 뒤, 저장 방식별로 insert한다. 이 테스트는 parse/process 시간과 write 시간을 나누어 로그로 남긴다.

기존 테스트는 `JdbcBatchItemWriter` 단독 측정이다.

```text
src/test/java/com/nhnacademy/ailibrarymyself/batch/init/BookBatchWriterPerformanceTest.java
```

방식별 비교 테스트는 별도 클래스로 추가했다.

```text
src/test/java/com/nhnacademy/ailibrarymyself/batch/init/BookWriterStrategyComparisonPerformanceTest.java
```

비교 테스트는 같은 `Book` 테스트 데이터 N건을 각 방식에 넣는다. 각 방식 실행 전에는 `${book.performance.schema}.books` 테이블과 `${book.performance.schema}.book_sequence`를 다시 만들고, 저장된 row count가 N건인지 검증한다. 기본 H2 테스트에서는 schema 기본값이 `public`이다.

이 보조 테스트의 비교 대상은 아래 4개다.

| strategy 로그 이름 | 의미 |
| --- | --- |
| `JPA saveAll` | N건 전체를 `saveAll()`로 저장 |
| `Simple chunk saveAll` | Spring Batch 없이 for-loop chunk 단위로 `saveAll()` 저장 |
| `JdbcTemplate.batchUpdate` | 직접 `PreparedStatement` 매핑으로 JDBC batch insert |
| `JdbcBatchItemWriter` | Spring Batch Writer 객체만 직접 호출해서 chunk 단위 insert |

일반 테스트 실행에서는 skip된다. 성능 로그를 보고 싶을 때만 옵션을 켠다.

```bash
./mvnw -q \
  -Dtest=BookWriterStrategyComparisonPerformanceTest \
  -Dbook.writer.performance=true \
  -Dbook.writer.performance.items=10000 \
  -Dbook.writer.performance.chunk-size=1000 \
  test
```

옵션 의미는 아래와 같다.

| 옵션 | 기본값 | 의미 |
| --- | --- | --- |
| `-Dbook.writer.performance=true` | false | 이 값이 true일 때만 성능 비교 테스트 실행 |
| `-Dbook.writer.performance.items=10000` | 10000 | 방식별 insert 대상 도서 수 |
| `-Dbook.writer.performance.chunk-size=1000` | 1000 | 청크 기반 방식의 chunk 크기 |

H2 test profile에서 100건, chunk size 25로 실행한 로그 예시는 아래와 같다.

```text
Book writer strategy performance result. strategy=JPA saveAll, items=100, chunkSize=25, elapsedMillis=224, elapsedSeconds=0.224
Book writer strategy performance result. strategy=Simple chunk saveAll, items=100, chunkSize=25, elapsedMillis=56, elapsedSeconds=0.056
Book writer strategy performance result. strategy=JdbcTemplate.batchUpdate, items=100, chunkSize=25, elapsedMillis=19, elapsedSeconds=0.019
Book writer strategy performance result. strategy=JdbcBatchItemWriter, items=100, chunkSize=25, elapsedMillis=58, elapsedSeconds=0.058
```

이 Writer 단독 테스트는 저장 방식만 빠르게 보는 보조 테스트다. Spring Batch Step까지 포함한 설명에는 아래 CSV 파싱 포함 테스트 결과를 사용한다.

## CSV 파싱 포함 성능 검증 테스트

실제 초기 적재에 더 가까운 비교 테스트도 추가했다.

```text
src/test/java/com/nhnacademy/ailibrarymyself/batch/init/BookCsvImportStrategyPerformanceTest.java
```

이 테스트는 아래 흐름을 방식별로 반복한다.

```text
CsvBookRawDataReader
  -> BookRawDataProcessor
  -> 저장 전략별 insert
  -> row count 검증
```

로그는 아래 값을 분리해서 출력한다.

| 로그 필드 | 의미 |
| --- | --- |
| `rawReadCount` | CSV에서 읽은 raw row 수 |
| `processedCount` | Processor 통과 후 저장 대상이 된 Book 수 |
| `skippedCount` | ISBN/title 누락 등으로 Processor에서 제외된 수 |
| `parseProcessMillis` | CSV read + raw data parsing + processor 변환 시간 |
| `writeMillis` | DB insert 시간 |
| `batchOverheadMillis` | Spring Batch Job/Step/chunk 실행에서 parse/write 외에 든 시간 |
| `totalMillis` | 전체 실행 시간 |

일반 테스트 실행에서는 skip된다. 실제 CSV 기준 성능을 보고 싶을 때만 옵션을 켠다.

```bash
./mvnw -q \
  -Dtest=BookCsvImportStrategyPerformanceTest \
  -Dbook.import.performance=true \
  -Dbook.import.performance.items=150000 \
  -Dbook.import.performance.chunk-size=1000 \
  test
```

옵션 의미는 아래와 같다.

| 옵션 | 기본값 | 의미 |
| --- | --- | --- |
| `-Dbook.import.performance=true` | false | 이 값이 true일 때만 CSV 포함 성능 비교 테스트 실행 |
| `-Dbook.import.performance.file=init/BOOK_DB_202112.csv` | `init/BOOK_DB_202112.csv` | 성능 측정에 사용할 CSV 파일 |
| `-Dbook.import.performance.items=150000` | 150000 | CSV에서 읽을 raw row 수. 0 이하이면 파일 끝까지 읽음 |
| `-Dbook.import.performance.chunk-size=1000` | 1000 | chunk 기반 저장 방식의 chunk 크기 |

아래는 기존 H2 test profile에서 CSV 150000건, chunk size 1000으로 실행했던 참고 로그다. 현재 `BookCsvImportStrategyPerformanceTest`는 PostgreSQL 전용이다.

```text
Book CSV import strategy performance result. strategy=Plain JPA saveAll, file=init/BOOK_DB_202112.csv, rawReadCount=150000, processedCount=150000, skippedCount=0, chunkSize=1000, parseProcessMillis=2500, writeMillis=9956, batchOverheadMillis=0, totalMillis=12456, totalSeconds=12.456
Book CSV import strategy performance result. strategy=Spring Batch + JPA saveAll, file=init/BOOK_DB_202112.csv, rawReadCount=150000, processedCount=150000, skippedCount=0, chunkSize=1000, parseProcessMillis=1885, writeMillis=4296, batchOverheadMillis=1540, totalMillis=7723, totalSeconds=7.723
Book CSV import strategy performance result. strategy=Spring Batch + JdbcTemplate.batchUpdate, file=init/BOOK_DB_202112.csv, rawReadCount=150000, processedCount=150000, skippedCount=0, chunkSize=1000, parseProcessMillis=1862, writeMillis=2270, batchOverheadMillis=1055, totalMillis=5188, totalSeconds=5.188
Book CSV import strategy performance result. strategy=Spring Batch + JdbcBatchItemWriter, file=init/BOOK_DB_202112.csv, rawReadCount=150000, processedCount=150000, skippedCount=0, chunkSize=1000, parseProcessMillis=1503, writeMillis=2667, batchOverheadMillis=954, totalMillis=5124, totalSeconds=5.124
```

## PostgreSQL 기준 성능 측정

H2는 메모리 DB라 실제 PostgreSQL보다 훨씬 빠를 수 있다. 이 테스트는 `performance` profile로 고정되어 있고, PostgreSQL이 아니면 실패한다.

테스트 전용 설정 파일은 아래에 있다.

```text
src/test/resources/application-performance.properties
```

기본 연결값은 로컬 성능 측정용 PostgreSQL이다. `currentSchema`에는 성능 측정 schema와 `public`을 같이 넣는다. `public`에 pgvector extension이 설치된 경우 `embedding vector` 타입을 찾기 위해 `public`이 search path에 필요할 수 있다.

```properties
spring.datasource.url=${BOOK_PERFORMANCE_DB_URL:jdbc:postgresql://localhost:15432/book_perf?rewriteBatchedInserts=true&stringtype=unspecified&currentSchema=${book.performance.schema},public}
spring.datasource.username=${BOOK_PERFORMANCE_DB_USERNAME:book}
spring.datasource.password=${BOOK_PERFORMANCE_DB_PASSWORD:book}
```

Docker로 전용 PostgreSQL을 띄우는 예시는 아래와 같다.

```bash
docker run --name book-perf-postgres \
  -e POSTGRES_DB=book_perf \
  -e POSTGRES_USER=book \
  -e POSTGRES_PASSWORD=book \
  -p 15432:5432 \
  -d pgvector/pgvector:pg16
```

같은 DB 안에서 별도 schema를 쓸 때 pgAdmin에서 먼저 실행할 SQL은 아래와 같다.

```sql
CREATE SCHEMA IF NOT EXISTS book_perf;

SELECT extname, extnamespace::regnamespace
FROM pg_extension
WHERE extname = 'vector';
```

두 번째 쿼리 결과가 있으면 같은 DB에 pgvector extension이 이미 설치된 것이다. 예를 들어 결과가 `vector | public`이면 JDBC URL의 `currentSchema`에 `book_perf,public`을 넣는다.

관리자 권한이 있고 extension이 아직 없다면 아래를 한 번만 실행한다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

테스트가 직접 만드는 객체는 아래 객체들이다.

```text
book_perf.books
book_perf.book_sequence
public.book_sequence
```

테스트는 각 방식 실행 전에 위 객체를 삭제하고 다시 만든다. `public.book_sequence`는 `Book` 엔티티의 JPA sequence 설정이 `public.book_sequence`로 고정되어 있어서 함께 재생성한다.

실행 명령은 아래와 같다.

```bash
./mvnw -q \
  -Dtest=BookCsvImportStrategyPerformanceTest \
  -Dbook.import.performance=true \
  -Dbook.import.performance.chunk-size=1000 \
  test
```

다른 DB를 쓰려면 환경변수로 연결 정보를 넘긴다.

```bash
BOOK_PERFORMANCE_SCHEMA="book_perf" \
BOOK_PERFORMANCE_DB_URL="jdbc:postgresql://host:5432/book_perf?rewriteBatchedInserts=true&stringtype=unspecified&currentSchema=book_perf,public" \
BOOK_PERFORMANCE_DB_USERNAME="book" \
BOOK_PERFORMANCE_DB_PASSWORD="book" \
./mvnw -q \
  -Dtest=BookCsvImportStrategyPerformanceTest \
  -Dbook.import.performance=true \
  -Dbook.import.performance.chunk-size=1000 \
  test
```

이 테스트는 각 방식 실행 전에 `${book.performance.schema}.books` 테이블과 `${book.performance.schema}.book_sequence`를 지웠다가 다시 만든다. 따라서 같은 DB를 쓰더라도 공용 `public` schema가 아니라 성능 측정 전용 schema에서 실행한다.

같은 DB 안에서 별도 schema를 쓸 때는 schema 이름을 환경변수로 넘긴다. DB명은 기존 DB명을 쓰고, schema만 `book_perf` 같은 전용 schema로 둔다.

```bash
BOOK_PERFORMANCE_SCHEMA="book_perf" \
BOOK_PERFORMANCE_DB_URL="jdbc:postgresql://host:5432/db?rewriteBatchedInserts=true&stringtype=unspecified&currentSchema=book_perf,public" \
BOOK_PERFORMANCE_DB_USERNAME="user" \
BOOK_PERFORMANCE_DB_PASSWORD="password" \
./mvnw -q \
  -Dtest=BookCsvImportStrategyPerformanceTest \
  -Dbook.import.performance=true \
  -Dbook.import.performance.chunk-size=1000 \
  test
```

현재 프로젝트의 Maven clean build는 Lombok/Querydsl annotation processing 설정에 민감하다. `clean` 후 `QBook`, Lombok getter, `log` 관련 컴파일 오류가 나면 성능 테스트 문제가 아니라 빌드 설정 문제다. 그 경우에는 먼저 Maven annotation processor 설정을 정리한 뒤 `clean`을 붙여 실행한다.

이 결과를 설명할 때는 아래처럼 구분한다.

```text
Writer 단독 테스트
  -> 저장 방식 자체의 성능 비교

CSV 포함 테스트
  -> 실제 초기 적재에 가까운 read/parse/process/write 전체 비교
```

주의할 점은 이 테스트가 H2 기준이라는 것이다. 실제 운영 PostgreSQL 성능은 네트워크, 인덱스, sequence, JDBC URL 옵션의 영향을 받는다.

또 하나 주의할 점은 Maven 성능 테스트를 동시에 여러 개 병렬 실행하지 않는 것이다. 같은 프로젝트의 `target/generated-sources`와 `target/surefire-reports`를 공유하므로, Querydsl annotation processing 결과가 깨질 수 있다. 성능 측정은 한 명령이 끝난 뒤 다음 명령을 실행한다.

PostgreSQL에서 batch insert 성능을 볼 때는 아래 옵션도 검토한다.

```properties
spring.datasource.url=jdbc:postgresql://host:port/db?rewriteBatchedInserts=true
```

## 최종 선택 기준

현재 프로젝트에서는 아래 기준으로 정리한다.

| 기준 | 선택 |
| --- | --- |
| 배치 실행 관리 | Spring Batch |
| CSV 읽기 | `ItemReader<BookRawData>` |
| 검증/정제/변환 | `ItemProcessor<BookRawData, Book>` |
| 저장 | `JdbcBatchItemWriter<Book>` |
| 성능 확인 | `BookBatchWriterPerformanceTest`, `BookWriterStrategyComparisonPerformanceTest`, `BookCsvImportStrategyPerformanceTest` |

이 구조가 지금 단계에서 가장 균형이 좋다. 이벤트 방식보다 안정적이고, JPA `saveAll()`보다 빠르며, 직접 `JdbcTemplate.batchUpdate()`보다 코드가 덜 복잡하다.
