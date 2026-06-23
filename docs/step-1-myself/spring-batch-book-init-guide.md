# 도서 초기 데이터 적재를 Spring Batch로 바꾸는 방법

이 문서는 현재 프로젝트 기준인 Spring Boot `3.5.10`, Spring Batch `5.2.4`, Spring AI `1.1.4` 조합을 기준으로 작성한다.

## 왜 이벤트 방식보다 Spring Batch가 맞는가

현재 프로젝트는 `CsvBookParser`가 CSV를 직접 읽고 `BookRawData`를 만든 뒤, 이벤트를 발행해서 저장하는 방향으로 시작되어 있다.

```text
CSV 파싱
  -> BookParsedEvent 여러 번 발행
  -> Listener가 한 건씩 저장 또는 모아서 저장
  -> BookParsingCompleteEvent로 마지막 flush
```

이 구조는 연습용으로는 이해하기 쉽지만, 대량 데이터를 초기 적재하는 코드로는 약하다.

- 이벤트는 "업무 사건 알림"에 가깝고, 대량 데이터 처리 흐름을 관리하는 도구가 아니다.
- 실패한 지점부터 재시작하기 어렵다.
- 몇 건 읽었고, 몇 건 저장했고, 어디서 실패했는지 추적하기 어렵다.
- Listener가 임시 버퍼를 직접 들고 있으면 트랜잭션 경계와 메모리 관리가 애매해진다.
- 마지막 완료 이벤트가 누락되면 남은 데이터가 저장되지 않는 식의 버그가 생기기 쉽다.

Spring Batch는 이런 문제를 해결하려고 만든 프레임워크다. 공식 문서에서도 가장 일반적인 방식으로 `ItemReader`가 한 건씩 읽고, `ItemProcessor`가 가공하고, 일정 개수의 chunk가 모이면 `ItemWriter`가 한 번에 쓰고 트랜잭션을 커밋하는 구조를 설명한다.

참고:

- Spring Batch Chunk-oriented Processing: https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html
- Spring Batch ItemReader, ItemProcessor, ItemWriter: https://docs.spring.io/spring-batch/reference/readers-and-writers.html
- Spring Batch Item processing: https://docs.spring.io/spring-batch/reference/processor.html

## Spring Batch의 기본 구조

Spring Batch는 보통 아래 단위로 구성한다.

```text
Job
  -> Step
      -> ItemReader
      -> ItemProcessor
      -> ItemWriter
```

이 프로젝트의 도서 초기 데이터 적재에 대입하면 다음과 같다.

| Batch 구성 요소 | 이 프로젝트에서의 역할 |
| --- | --- |
| `Job` | 도서 CSV 초기 적재 작업 전체 |
| `Step` | CSV를 읽어서 DB에 저장하는 한 단계 |
| `ItemReader<BookRawData>` | CSV 파일에서 한 줄씩 읽어 `BookRawData`로 변환 |
| `ItemProcessor<BookRawData, Book>` | ISBN, 빈 값 등 검증/정제 후 저장할 `Book` 엔티티로 변환 |
| `ItemWriter<Book>` | chunk 단위로 DB 저장 |

중요한 점은 `ItemWriter`가 한 건씩 호출되는 것이 아니라 chunk 단위로 호출된다는 것이다. 예를 들어 chunk size가 1000이면 reader/processor는 한 건씩 동작하지만, writer는 최대 1000건 묶음으로 저장한다.

## 의존성 추가

`pom.xml`에 Spring Batch starter를 추가한다.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>
```

Spring Batch는 실행 이력 관리를 위해 metadata table을 사용한다. 로컬 개발 초기에는 Boot가 schema를 만들게 둘 수 있다.

```properties
spring.batch.jdbc.initialize-schema=always
```

운영 DB에서는 `always`를 그대로 두기보다 schema를 직접 관리하거나 환경별로 분리하는 편이 낫다.

## 설정 방향

현재 `application.properties`에는 이미 초기 데이터 설정이 있다.

```properties
init.book_file=data/init/BOOK_DB_202112.csv
init.enable=false
init.batch_size=1000
```

이 값은 Spring Batch에서도 그대로 쓸 수 있다.

- `init.book_file`: CSV 파일 위치
- `init.enable`: 애플리케이션 시작 시 자동 실행 여부
- `init.batch_size`: chunk size

다만 지금 값의 파일 경로는 주의해야 한다. 현재 리소스 파일은 `src/main/resources/init/BOOK_DB_202112.csv`에 있으므로 classpath 기준이면 아래처럼 맞추는 편이 자연스럽다.

```properties
init.book_file=init/BOOK_DB_202112.csv
```

## 권장 패키지 구조

현재 구조에 맞춰 아래처럼 나누는 것을 추천한다.

```text
com.nhnacademy.ailibrarymyself.batch.init
  CsvBookRawDataReader.java
  BookRawDataProcessor.java
  BookInitJobRunner.java

com.nhnacademy.ailibrarymyself.batch.init.config
  BookInitBatchConfig.java

com.nhnacademy.ailibrarymyself.batch.init.dto
  BookRawData.java

com.nhnacademy.ailibrarymyself.batch.init.properties
  InitProperties.java
```

기존 `CsvBookParser`는 Spring Batch로 옮기면 없어져도 된다. 파싱 책임은 `ItemReader`가 가져간다.

## `@StepScope`는 무엇인가

`@StepScope`는 bean을 애플리케이션 시작 시점에 바로 만들지 않고, Step이 실행될 때 만들도록 하는 Spring Batch scope다.

일반 singleton bean은 애플리케이션이 뜰 때 한 번 생성된다. 반면 `@StepScope` bean은 Step 실행 단위로 생성된다. 그래서 reader처럼 "이번 Step 실행에서만 필요한 상태"를 가진 객체에 잘 맞는다.

Reader에 `@StepScope`를 붙이는 이유는 보통 아래 때문이다.

- CSV 파일 경로 같은 JobParameter를 Step 실행 시점에 받을 수 있다.
- reader 내부에 `CSVParser`, `Reader`, `Iterator` 같은 실행 상태를 안전하게 둘 수 있다.
- 같은 reader bean이 여러 Job 실행 사이에서 상태를 공유하는 문제를 피할 수 있다.
- 테스트할 때 Step 실행 단위로 reader 상태를 분리하기 쉽다.

특히 `ItemReader`는 `read()`가 여러 번 호출되면서 내부 cursor 상태를 가진다. 그래서 singleton reader에 상태를 들고 있으면 재실행, 병렬 실행, 테스트에서 문제가 생기기 쉽다. 상태가 없는 processor는 singleton으로 둬도 괜찮지만, 파일을 열고 순회하는 reader는 `@StepScope`를 붙이는 편이 낫다.

## Reader 리소스 close 처리

파일 reader를 직접 만들면 반드시 닫는 처리가 필요하다. `InputStream`, `Reader`, `CSVParser`를 열고 닫지 않으면 파일 핸들이 남을 수 있다.

Spring Batch에서는 이런 경우 `ItemStreamReader`를 구현하는 방식이 자연스럽다.

```text
open()
  파일 열기
  CSVParser 생성
  iterator 준비

read()
  한 줄씩 읽어서 item 반환
  더 읽을 게 없으면 null 반환

close()
  CSVParser, Reader 닫기
```

Step에 등록된 reader가 `ItemStream`을 구현하면 Spring Batch가 Step 시작/종료 시점에 `open`, `update`, `close`를 호출해준다.

## BatchConfig 예시

아래 코드는 방향을 보여주는 예시다. 아직 프로젝트에 `Book` 엔티티나 Repository가 없으므로 writer 내부 저장 코드는 실제 엔티티가 생긴 뒤 맞춰야 한다.

```java
package com.nhnacademy.ailibrarymyself.batch.init.config;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarymyself.batch.init.properties.InitProperties;
import com.nhnacademy.ailibrarymyself.batch.init.CsvBookRawDataReader;
import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class BookInitBatchConfig {

    private static final String BOOK_INSERT_SQL = """
            INSERT INTO books (
                id,
                isbn,
                title,
                author_name,
                publisher_name,
                first_publish_date,
                price,
                image_url,
                book_content,
                category,
                subtitle,
                edition_publish_date,
                created_at
            ) VALUES (
                nextval('public.book_sequence'),
                :isbn,
                :title,
                :authorName,
                :publisherName,
                :firstPublishDate,
                :price,
                :imageUrl,
                :bookContent,
                :category,
                :subtitle,
                :editionPublishDate,
                CURRENT_TIMESTAMP
            )
            """;

    private final InitProperties initProperties;

    @Bean
    public Job bookInitJob(JobRepository jobRepository, Step bookInitStep) {
        return new JobBuilder("bookInitJob", jobRepository)
                .start(bookInitStep)
                .build();
    }

    @Bean
    public Step bookInitStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<BookRawData> bookRawDataReader,
            ItemProcessor<BookRawData, Book> bookRawDataProcessor,
            ItemWriter<Book> bookRawDataWriter
    ) {
        return new StepBuilder("bookInitStep", jobRepository)
                .<BookRawData, Book>chunk(initProperties.getBatchSize(), transactionManager)
                .reader(bookRawDataReader)
                .processor(bookRawDataProcessor)
                .writer(bookRawDataWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<BookRawData> bookRawDataReader() {
        return new CsvBookRawDataReader(initProperties.getBookFile());
    }

    @Bean
    public JdbcBatchItemWriter<Book> bookRawDataWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Book>()
                .dataSource(dataSource)
                .sql(BOOK_INSERT_SQL)
                .beanMapped()
                .build();
    }
}
```

`@StepScope`를 붙인 reader는 Step 실행 시점에 생성된다. 지금 예시는 `InitProperties`에서 파일 경로를 가져오지만, 나중에 JobParameter로 파일 경로를 넘기고 싶으면 아래처럼 바꿀 수 있다.

```java
@Bean
@StepScope
public ItemReader<BookRawData> bookRawDataReader(
        @Value("#{jobParameters['bookFile']}") String bookFile
) {
    return new CsvBookRawDataReader(bookFile);
}
```

## Reader 클래스 예시

파일을 직접 여는 reader는 설정 클래스 안에 내부 클래스로 두기보다 별도 클래스로 빼는 편이 좋다. 코드가 길고, 상태와 리소스 관리 책임이 있기 때문이다.

```java
package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class CsvBookRawDataReader implements ItemStreamReader<BookRawData> {

    private static final String CSV_HEADER_SEQ = "SEQ_NO";
    private static final String CSV_HEADER_ISBN = "ISBN_THIRTEEN_NO";
    private static final String CSV_HEADER_TITLE = "TITLE_NM";
    private static final String CSV_HEADER_AUTHOR = "AUTHR_NM";
    private static final String CSV_HEADER_PUBLISHER = "PUBLISHER_NM";
    private static final String CSV_HEADER_PUB_DATE = "PBLICTE_DE";
    private static final String CSV_HEADER_PRICE = "PRC_VALUE";
    private static final String CSV_HEADER_IMAGE = "IMAGE_URL";
    private static final String CSV_HEADER_CONTENT = "BOOK_INTRCN_CN";
    private static final String CSV_HEADER_CATEGORY = "KDC_NM";
    private static final String CSV_HEADER_SUBTITLE = "TITLE_SBST_NM";
    private static final String CSV_HEADER_EDITION_DATE = "TWO_PBLICTE_DE";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String file;
    private Reader reader;
    private CSVParser csvParser;
    private Iterator<CSVRecord> iterator;

    public CsvBookRawDataReader(String file) {
        this.file = file;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            reader = new InputStreamReader(
                    new ClassPathResource(file).getInputStream(),
                    StandardCharsets.UTF_8
            );

            csvParser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .get()
                    .parse(reader);

            iterator = csvParser.iterator();
        } catch (IOException e) {
            throw new ItemStreamException("Failed to open book csv file: " + file, e);
        }
    }

    @Override
    public BookRawData read() {
        if (iterator == null || !iterator.hasNext()) {
            return null;
        }

        CSVRecord record = iterator.next();
        BookRawData book = new BookRawData();

        book.setId(parseLong(record.get(CSV_HEADER_SEQ)));
        book.setIsbn(record.get(CSV_HEADER_ISBN));
        book.setTitle(record.get(CSV_HEADER_TITLE));
        book.setAuthorName(record.get(CSV_HEADER_AUTHOR));
        book.setPublisherName(record.get(CSV_HEADER_PUBLISHER));
        book.setFirstPublishDate(parseDate(record.get(CSV_HEADER_PUB_DATE)));
        book.setPrice(parseBigDecimal(record.get(CSV_HEADER_PRICE)));
        book.setImageUrl(record.get(CSV_HEADER_IMAGE));
        book.setBookContent(record.get(CSV_HEADER_CONTENT));
        book.setCategory(record.get(CSV_HEADER_CATEGORY));
        book.setSubtitle(record.get(CSV_HEADER_SUBTITLE));
        book.setEditionPublishDate(parseDate(record.get(CSV_HEADER_EDITION_DATE)));

        return book;
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            throw new ItemStreamException("Failed to close book csv file: " + file, e);
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(normalized, BASIC_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
```

참고로 이 예시는 `close()`를 보여주기 위해 직접 reader를 만든 방식이다. 단순 CSV라면 Spring Batch가 제공하는 `FlatFileItemReader`를 쓰는 것도 좋은 선택이다. 내장 reader를 쓰면 파일 open/close 같은 생명주기 처리를 프레임워크가 더 잘 맡아준다.

### `FlatFileItemReader`를 쓰는 방식

위에서 직접 만든 `CsvBookRawDataReader`는 CSV를 여는 과정과 닫는 과정을 직접 코드로 보여주기 위한 예시다. 실제 프로젝트에서는 단순한 CSV 파일이라면 Spring Batch가 제공하는 `FlatFileItemReader`를 먼저 고려하는 게 좋다.

`FlatFileItemReader`를 쓰면 reader가 해야 하는 아래 작업을 프레임워크가 맡는다.

- 파일 열기
- 인코딩 처리
- header line skip
- 한 줄씩 읽기
- Step 종료 시 파일 닫기
- 실행 상태 저장

이 경우 개발자가 작성할 핵심 코드는 "CSV 한 줄을 `BookRawData`로 어떻게 바꿀 것인가"다. 이 책임은 `FieldSetMapper`에 둔다.

Spring Boot 3.5.10 기준으로 들어오는 Spring Batch 5.2.4에서는 아래 패키지를 사용한다.

```java
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
```

설정 코드는 아래처럼 만들 수 있다.

```java
import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

@Bean
@StepScope
public FlatFileItemReader<BookRawData> bookRawDataReader(
        InitProperties initProperties,
        BookRawDataFieldSetMapper fieldSetMapper
) {
    return new FlatFileItemReaderBuilder<BookRawData>()
            .name("bookRawDataReader")
            .resource(new ClassPathResource(initProperties.getBookFile()))
            .encoding("UTF-8")
            .linesToSkip(1)
            .delimited()
            .names(
                    "SEQ_NO",
                    "ISBN_THIRTEEN_NO",
                    "TITLE_NM",
                    "AUTHR_NM",
                    "PUBLISHER_NM",
                    "PBLICTE_DE",
                    "PRC_VALUE",
                    "IMAGE_URL",
                    "BOOK_INTRCN_CN",
                    "KDC_NM",
                    "TITLE_SBST_NM",
                    "TWO_PBLICTE_DE"
            )
            .fieldSetMapper(fieldSetMapper)
            .build();
}
```

`linesToSkip(1)`은 첫 줄 header를 건너뛰겠다는 뜻이다. `names(...)`는 각 컬럼에 이름을 붙이는 설정이다. 그러면 mapper에서 index가 아니라 컬럼 이름으로 값을 읽을 수 있다.

`FieldSetMapper`는 별도 클래스로 빼는 편이 좋다. reader 설정과 변환 로직을 분리할 수 있기 때문이다.

```java
package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class BookRawDataFieldSetMapper implements FieldSetMapper<BookRawData> {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public BookRawData mapFieldSet(FieldSet fieldSet) throws BindException {
        BookRawData book = new BookRawData();

        book.setId(parseLong(fieldSet.readString("SEQ_NO")));
        book.setIsbn(fieldSet.readString("ISBN_THIRTEEN_NO"));
        book.setTitle(fieldSet.readString("TITLE_NM"));
        book.setAuthorName(fieldSet.readString("AUTHR_NM"));
        book.setPublisherName(fieldSet.readString("PUBLISHER_NM"));
        book.setFirstPublishDate(parseDate(fieldSet.readString("PBLICTE_DE")));
        book.setPrice(parseBigDecimal(fieldSet.readString("PRC_VALUE")));
        book.setImageUrl(fieldSet.readString("IMAGE_URL"));
        book.setBookContent(fieldSet.readString("BOOK_INTRCN_CN"));
        book.setCategory(fieldSet.readString("KDC_NM"));
        book.setSubtitle(fieldSet.readString("TITLE_SBST_NM"));
        book.setEditionPublishDate(parseDate(fieldSet.readString("TWO_PBLICTE_DE")));

        return book;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(normalized, BASIC_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
```

이 방식에서는 `Reader`, `CSVParser`, `Iterator` 필드를 직접 들고 있을 필요가 없다. 그래서 `open()`과 `close()`도 직접 작성하지 않는다. `FlatFileItemReader`가 `ItemStreamReader` 구현체라서 Step 생명주기에 맞춰 파일을 열고 닫는다.

정리하면 선택 기준은 아래와 같다.

| 방식 | 언제 쓰면 좋은가 |
| --- | --- |
| 직접 만든 `ItemStreamReader` | CSV 구조가 복잡하거나 Apache Commons CSV 기능을 세밀하게 써야 할 때 |
| `FlatFileItemReader` | 일반적인 구분자 CSV를 읽고 DTO로 매핑하면 충분할 때 |

현재 도서 CSV가 일반적인 comma-separated CSV라면 `FlatFileItemReader + FieldSetMapper` 방식이 더 간단하고 Spring Batch다운 코드다.

## Processor는 왜 필요한가

`BookRawDataProcessor`는 reader가 읽어온 `BookRawData`를 검사하고 정리한 뒤, 실제 저장할 `Book` 엔티티로 바꾸는 단계다.

Reader는 "CSV를 읽어서 DTO로 바꾸는 일"에 집중하고, Writer는 "DB에 저장하는 일"에 집중하는 게 좋다. 그러면 저장 전에 버릴 데이터나 정리할 데이터는 Processor에서 처리하는 것이 자연스럽다.

현재 단계에서 Processor가 할 일은 많지 않다.

- 문자열 앞뒤 공백 제거
- 빈 문자열을 `null`로 정리
- ISBN이 없는 데이터 제외
- 제목이 없는 데이터 제외
- 저장할 `Book` 엔티티로 변환

Spring Batch에서 `ItemProcessor`가 `null`을 반환하면 그 item은 filter 처리되어 writer로 넘어가지 않는다. 즉, 잘못된 데이터를 저장 단계까지 보내지 않는 용도로 쓸 수 있다.

```java
package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookRawDataProcessor implements ItemProcessor<BookRawData, Book> {

    @Override
    public Book process(BookRawData item) {
        trimTextFields(item);

        if (isBlank(item.getIsbn())) {
            log.debug("skip book raw data. reason=missing isbn, id={}, title={}", item.getId(), item.getTitle());
            return null;
        }

        if (isBlank(item.getTitle())) {
            log.debug("skip book raw data. reason=missing title, id={}, isbn={}", item.getId(), item.getIsbn());
            return null;
        }

        return toEntity(item);
    }

    private void trimTextFields(BookRawData item) {
        item.setIsbn(trimToNull(item.getIsbn()));
        item.setTitle(trimToNull(item.getTitle()));
        item.setAuthorName(trimToNull(item.getAuthorName()));
        item.setPublisherName(trimToNull(item.getPublisherName()));
        item.setImageUrl(trimToNull(item.getImageUrl()));
        item.setBookContent(trimToNull(item.getBookContent()));
        item.setCategory(trimToNull(item.getCategory()));
        item.setSubtitle(trimToNull(item.getSubtitle()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Book toEntity(BookRawData item) {
        return new Book(
                item.getIsbn(),
                item.getTitle(),
                item.getAuthorName(),
                item.getPublisherName(),
                item.getFirstPublishDate(),
                item.getPrice(),
                item.getImageUrl(),
                item.getBookContent(),
                item.getCategory(),
                item.getSubtitle(),
                item.getEditionPublishDate()
        );
    }
}
```

Processor에서 `BookRawData`를 `Book`으로 바꾸면 Writer는 저장만 담당한다. 다만 Repository를 호출해서 DB 조회/저장을 하는 로직은 Processor보다 Writer에 두는 편이 낫다.

## Writer는 어디까지 책임져야 하는가

Writer는 "저장" 책임만 갖는 게 좋다. 파싱, 정제, 검증까지 writer에 몰아넣으면 이벤트 리스너 방식과 비슷하게 다시 복잡해진다.

권장 분리는 아래와 같다.

```text
Reader
  CSV 한 줄을 BookRawData로 변환

Processor
  필수값 검증
  날짜/가격 정제
  저장하지 않을 데이터 filter
  저장할 Book 엔티티로 변환

Writer
  JDBC batch insert로 DB 저장
```

Writer는 직접 `PreparedStatement`를 다루는 클래스로 만들 수도 있지만, 현재 프로젝트에서는 Spring Batch가 제공하는 `JdbcBatchItemWriter`를 쓰는 편이 균형이 좋다.

- JPA `saveAll()`보다 대량 insert 성능이 좋다.
- 직접 `JdbcTemplate.batchUpdate()`를 구현하는 것보다 코드가 짧다.
- Spring Batch 표준 Writer라 Step 구성과 잘 맞는다.
- `beanMapped()`를 쓰면 `:isbn`, `:title` 같은 이름을 `Book` getter와 매핑할 수 있다.

초기 도서 데이터는 한 번 대량 적재하는 성격이 강하다. 이 경우 JPA `saveAll()`보다 `JdbcBatchItemWriter`가 더 빠를 수 있다. Spring Batch를 버리는 것이 아니라, Spring Batch의 chunk 처리 안에서 저장 방식만 JDBC batch insert로 바꾸는 것이다.

예시는 아래처럼 잡을 수 있다.

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

SQL은 named parameter를 쓴다. `:isbn`은 `book.getIsbn()`, `:authorName`은 `book.getAuthorName()`과 매핑된다.

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

## 이벤트는 완전히 버려야 하나

버리는 편이 낫다기보다, 역할을 바꿔야 한다.

대량 저장의 메인 흐름은 Spring Batch가 맡고, 이벤트는 부가 작업에만 쓰는 것이 좋다.

좋은 이벤트 사용 예:

- 배치 완료 후 관리자 알림 발송
- 배치 실패 후 Telegram 알림 발송
- 검색 인덱스 갱신 요청
- 통계 캐시 무효화

좋지 않은 이벤트 사용 예:

- CSV 한 줄마다 `BookParsedEvent` 발행
- Listener 내부 List에 데이터를 모았다가 완료 이벤트 때 저장
- 완료 이벤트가 flush 트리거 역할을 함

현재 프로젝트처럼 "배치가 끝났는지 로그만 남기면 되는 정도"라면 listener를 굳이 만들 필요 없다. `JobLauncher.run(...)`은 `JobExecution`을 반환하므로, runner에서 상태를 바로 로그로 남기면 된다.

```java
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookInitJobRunner implements ApplicationRunner {

    private final InitProperties initProperties;
    private final JobLauncher jobLauncher;
    private final Job bookInitJob;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!initProperties.isEnable()) {
            return;
        }

        JobExecution jobExecution = jobLauncher.run(
                bookInitJob,
                new JobParametersBuilder()
                        .addLong("requestedAt", System.currentTimeMillis())
                        .toJobParameters()
        );

        log.info("book init job finished. status={}", jobExecution.getStatus());
    }
}
```

반대로 배치 완료 후 Telegram 알림, 실패 알림, 통계 저장, 검색 인덱스 갱신처럼 "배치 실행 결과에 따라 별도 후속 작업"이 필요하면 그때 `JobExecutionListener`를 분리해서 쓰면 된다. 지금 단계에서는 runner 로그만으로 충분하다.

## 애플리케이션 시작 시 실행하기

`init.enable=true`일 때만 실행하고 싶으면 `ApplicationRunner`에서 `JobLauncher`를 호출할 수 있다.

```java
package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.properties.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookInitJobRunner implements ApplicationRunner {

    private final InitProperties initProperties;
    private final JobLauncher jobLauncher;
    private final Job bookInitJob;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!initProperties.isEnable()) {
            return;
        }

        JobExecution jobExecution = jobLauncher.run(
                bookInitJob,
                new JobParametersBuilder()
                        .addLong("requestedAt", System.currentTimeMillis())
                        .toJobParameters()
        );

        log.info("book init job finished. status={}", jobExecution.getStatus());
    }
}
```

`requestedAt` 같은 JobParameter를 넣는 이유는 같은 JobParameters로 이미 성공한 Job은 다시 실행되지 않기 때문이다.

## 삭제해도 되는 기존 파일

Spring Batch 구조로 옮기면 기존 파서/이벤트 기반 흐름은 더 이상 필요하지 않다.

삭제해도 되는 파일은 아래와 같다.

| 파일 | 삭제해도 되는 이유 |
| --- | --- |
| `BookParser.java` | CSV 파싱 추상화였지만, 이제 `ItemReader`가 읽기 책임을 가진다. |
| `CsvBookParser.java` | CSV 직접 순회 로직이었지만, 이제 `CsvBookRawDataReader` 또는 `FlatFileItemReader`가 대체한다. |
| `BookParsedEvent.java` | 한 줄 파싱마다 이벤트를 날리는 구조를 쓰지 않는다. |
| `BookParsingCompleteEvent.java` | 마지막 flush 이벤트가 필요 없다. chunk 저장은 Spring Batch가 관리한다. |

삭제 후에는 `rg "CsvBookParser|BookParser|BookParsedEvent|BookParsingCompleteEvent" src/main/java`로 참조가 남아 있는지 확인하면 된다.

## 추천 도입 순서

1. `spring-boot-starter-batch` 의존성을 추가한다.
2. `spring.batch.jdbc.initialize-schema=always`를 로컬 설정에 추가한다.
3. `BookInitBatchConfig`를 만들고 reader/processor/writer 골격을 만든다.
4. 기존 `CsvBookParser`의 CSV header, 날짜 변환 코드를 reader로 이동한다.
5. 엔티티와 Repository가 준비되면 writer에서 chunk 단위 저장을 구현한다.
6. `BookParser`, `CsvBookParser`, `BookParsedEvent`, `BookParsingCompleteEvent`는 대량 저장 흐름에서 제거한다.
7. 단순 완료 로그는 `JobRunner`에서 `JobExecution` 상태를 로그로 남긴다.
8. 완료/실패 후속 작업이 필요해지면 그때 Spring Batch `JobExecutionListener` 또는 `StepExecutionListener`로 분리한다.

## 정리

이 프로젝트의 초기 도서 CSV 적재는 이벤트 기반 파서보다 Spring Batch가 더 적합하다.

핵심은 아래처럼 책임을 나누는 것이다.

```text
CsvBookParser + EventListener + CompleteEvent
```

를

```text
ItemReader + ItemProcessor + ItemWriter + Job/Step metadata
```

로 바꾼다.

이렇게 하면 chunk 단위 저장, 트랜잭션 관리, 실패 추적, 재실행 제어를 직접 구현하지 않고 Spring Batch의 표준 흐름으로 가져갈 수 있다.
