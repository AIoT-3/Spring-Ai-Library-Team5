package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataJpaTest
@ActiveProfiles("performance")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.data.jpa.repositories.enabled=false"
})
@EnabledIfSystemProperty(
        named = "book.import.performance",
        matches = "true",
        disabledReason = "Run with -Dbook.import.performance=true to execute this PostgreSQL performance test."
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookCsvImportStrategyPerformanceTest {

    private static final String DEFAULT_BOOK_FILE = "init/BOOK_DB_202112.csv";
    private static final int DEFAULT_RAW_ITEM_LIMIT = 150_000;
    private static final String DEFAULT_SCHEMA = "book_perf";
    private static final String EXTERNAL_DB_URL_ENV = "BOOK_PERFORMANCE_DB_URL";
    private static final String EXTERNAL_DB_URL_PROPERTY = "book.performance.db.url";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("book_perf")
            .withUsername("book")
            .withPassword("book");

    private static final String NAMED_PARAMETER_INSERT_SQL = """
            INSERT INTO %s (
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
                nextval('%s'),
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

    private static final String PREPARED_STATEMENT_INSERT_SQL = """
            INSERT INTO %s (
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
                nextval('%s'),
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final BookRawDataProcessor processor = new BookRawDataProcessor();

    @DynamicPropertySource
    static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        if (hasExternalPostgreSqlUrl()) {
            registry.add("spring.datasource.url", BookCsvImportStrategyPerformanceTest::externalPostgreSqlUrl);
            registry.add("spring.datasource.username", () -> externalValue("BOOK_PERFORMANCE_DB_USERNAME", "book"));
            registry.add("spring.datasource.password", () -> externalValue("BOOK_PERFORMANCE_DB_PASSWORD", "book"));
            registry.add("book.performance.schema", () -> externalValue("BOOK_PERFORMANCE_SCHEMA", DEFAULT_SCHEMA));
            return;
        }

        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));

        registry.add("spring.datasource.url", () -> postgresJdbcUrlWithPerformanceOptions(POSTGRES.getJdbcUrl()));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("book.performance.schema", () -> DEFAULT_SCHEMA);
    }

    @BeforeEach
    void setUp() {
        assertPostgreSql();
        recreateBooksTable();
    }

    @Test
    void compareCsvImportStrategies() throws Exception {
        String bookFile = System.getProperty("book.import.performance.file", DEFAULT_BOOK_FILE);
        int rawItemLimit = Integer.getInteger("book.import.performance.items", DEFAULT_RAW_ITEM_LIMIT);
        int chunkSize = Integer.getInteger("book.import.performance.chunk-size", 1_000);

        runPlainJpaSaveAllAndVerify(bookFile, rawItemLimit, chunkSize);
        runSpringBatchAndVerify("Spring Batch + JPA saveAll", bookFile, rawItemLimit, chunkSize, this::jpaSaveAllWriter);
        runSpringBatchAndVerify("Spring Batch + JdbcTemplate.batchUpdate", bookFile, rawItemLimit, chunkSize,
                this::jdbcTemplateBatchUpdateWriter);

        JdbcBatchItemWriter<Book> writer = new JdbcBatchItemWriterBuilder<Book>()
                .dataSource(dataSource)
                .sql(namedParameterInsertSql())
                .beanMapped()
                .build();
        writer.afterPropertiesSet();
        runSpringBatchAndVerify("Spring Batch + JdbcBatchItemWriter", bookFile, rawItemLimit, chunkSize,
                () -> writer);
    }

    private void runPlainJpaSaveAllAndVerify(String bookFile, int rawItemLimit, int chunkSize) throws Exception {
        recreateBooksTable();

        CsvImportTiming timing = importCsvAndWriteAll(bookFile, rawItemLimit, this::saveAllAtOnce);
        Integer savedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + booksTable(), Integer.class);

        logResult("Plain JPA saveAll", bookFile, timing.rawReadCount, timing.processedCount, chunkSize, timing);

        assertThat(savedCount)
                .as("Plain JPA saveAll saved row count")
                .isEqualTo(timing.processedCount);
    }

    private void runSpringBatchAndVerify(
            String strategyName,
            String bookFile,
            int rawItemLimit,
            int chunkSize,
            SpringBatchWriterFactory writerFactory
    ) throws Exception {
        recreateBooksTable();

        CsvImportTiming timing = new CsvImportTiming();
        JobRepository jobRepository = new ResourcelessJobRepository();
        ItemStreamReader<BookRawData> reader = new TimedCsvBookRawDataReader(bookFile, rawItemLimit, timing);
        ItemProcessor<BookRawData, Book> timedProcessor = rawData -> {
            long processStart = System.nanoTime();
            try {
                Book book = processor.process(rawData);
                if (book != null) {
                    timing.processedCount++;
                }
                return book;
            } finally {
                timing.parseProcessNanos += System.nanoTime() - processStart;
            }
        };
        ItemWriter<Book> timedWriter = books -> {
            long writeStart = System.nanoTime();
            try {
                writerFactory.create().write(books);
            } finally {
                timing.writeNanos += System.nanoTime() - writeStart;
            }
        };

        Step step = new StepBuilder(strategyName + " step", jobRepository)
                .<BookRawData, Book>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(timedProcessor)
                .writer(timedWriter)
                .build();
        Job job = new JobBuilder(strategyName + " job", jobRepository)
                .start(step)
                .build();
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.afterPropertiesSet();

        long totalStart = System.nanoTime();
        JobExecution execution = jobLauncher.run(job, new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters());
        timing.totalNanos = System.nanoTime() - totalStart;

        Integer savedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + booksTable(), Integer.class);

        assertThat(execution.getExitStatus().getExitCode())
                .as("%s exit status", strategyName)
                .isEqualTo("COMPLETED");
        assertThat(savedCount)
                .as("%s saved row count", strategyName)
                .isEqualTo(timing.processedCount);

        logResult(strategyName, bookFile, timing.rawReadCount, timing.processedCount, chunkSize, timing);
    }

    private void logResult(
            String strategyName,
            String bookFile,
            int rawReadCount,
            int processedCount,
            int chunkSize,
            CsvImportTiming timing
    ) {
        log.info(
                "Book CSV import strategy performance result. strategy={}, file={}, rawReadCount={}, processedCount={}, skippedCount={}, chunkSize={}, parseProcessMillis={}, writeMillis={}, batchOverheadMillis={}, totalMillis={}, totalSeconds={}",
                strategyName,
                bookFile,
                rawReadCount,
                processedCount,
                timing.skippedCount(),
                chunkSize,
                timing.parseProcessMillis(),
                timing.writeMillis(),
                timing.batchOverheadMillis(),
                timing.totalMillis(),
                timing.totalMillis() / 1000.0
        );
    }

    private CsvImportTiming importCsvAndWriteAll(
            String bookFile,
            int rawItemLimit,
            BookChunkWriter writer
    ) throws Exception {
        CsvBookRawDataReader reader = new CsvBookRawDataReader(bookFile);
        CsvImportTiming timing = new CsvImportTiming();
        List<Book> books = new ArrayList<>();

        reader.open(new ExecutionContext());
        try {
            while (rawItemLimit <= 0 || timing.rawReadCount < rawItemLimit) {
                long parseStart = System.nanoTime();
                BookRawData rawData = reader.read();
                if (rawData == null) {
                    timing.parseProcessNanos += System.nanoTime() - parseStart;
                    break;
                }

                timing.rawReadCount++;
                Book book = processor.process(rawData);
                timing.parseProcessNanos += System.nanoTime() - parseStart;

                if (book == null) {
                    continue;
                }

                timing.processedCount++;
                books.add(book);
            }

            if (!books.isEmpty()) {
                writeChunk(writer, books, timing);
            }
        } finally {
            reader.close();
        }

        timing.totalNanos = timing.parseProcessNanos + timing.writeNanos;
        return timing;
    }

    private void writeChunk(BookChunkWriter writer, List<Book> chunk, CsvImportTiming timing) throws Exception {
        long writeStart = System.nanoTime();
        writer.write(chunk);
        timing.writeNanos += System.nanoTime() - writeStart;
    }

    private void saveAllAtOnce(List<Book> books) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SimpleJpaRepository<Book, Long> repository = new SimpleJpaRepository<>(Book.class, entityManager);
            repository.saveAll(books);
            repository.flush();
            entityManager.clear();
        });
    }

    private void saveChunkWithJpa(List<Book> books) {
        SimpleJpaRepository<Book, Long> repository = new SimpleJpaRepository<>(Book.class, entityManager);
        repository.saveAll(books);
        repository.flush();
        entityManager.clear();
    }

    private void saveChunkWithJdbcTemplate(List<Book> books) {
        jdbcTemplate.batchUpdate(preparedStatementInsertSql(), new BookBatchPreparedStatementSetter(books));
    }

    private ItemWriter<Book> jpaSaveAllWriter() {
        return books -> saveChunkWithJpa(new ArrayList<>(books.getItems()));
    }

    private ItemWriter<Book> jdbcTemplateBatchUpdateWriter() {
        return books -> saveChunkWithJdbcTemplate(new ArrayList<>(books.getItems()));
    }

    private void recreateBooksTable() {
        entityManager.clear();
        String embeddingColumnType = embeddingColumnType();
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName());
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + booksTable());
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS " + bookSequence());
        jdbcTemplate.execute("CREATE SEQUENCE " + bookSequence() + " START WITH 1 INCREMENT BY 1000");
        recreateJpaBookSequenceIfNeeded();
        jdbcTemplate.execute("""
                CREATE TABLE %s (
                    id BIGINT NOT NULL PRIMARY KEY,
                    isbn VARCHAR(20) NOT NULL UNIQUE,
                    title VARCHAR(500) NOT NULL,
                    author_name VARCHAR(1000),
                    publisher_name VARCHAR(255),
                    first_publish_date DATE,
                    price NUMERIC(10, 2),
                    image_url TEXT,
                    book_content TEXT,
                    category VARCHAR(20),
                    subtitle VARCHAR(500),
                    edition_publish_date DATE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE,
                    embedding %s
                )
                """.formatted(booksTable(), embeddingColumnType));
    }

    private void recreateJpaBookSequenceIfNeeded() {
        if ("public".equals(schemaName())) {
            return;
        }

        jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS public.book_sequence START WITH 1 INCREMENT BY 1000");
    }

    private void assertPostgreSql() {
        assertThat(databaseProductName())
                .as("BookCsvImportStrategyPerformanceTest must run against PostgreSQL")
                .isEqualTo("PostgreSQL");
    }

    private String databaseProductName() {
        return jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName()
        );
    }

    private String embeddingColumnType() {
        if (createVectorExtensionIfPossible()) {
            return "vector(1024)";
        }
        return "BYTEA";
    }

    private boolean createVectorExtensionIfPossible() {
        Boolean vectorAvailable = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector')",
                Boolean.class
        );
        if (!Boolean.TRUE.equals(vectorAvailable)) {
            return false;
        }

        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            return true;
        } catch (DataAccessException e) {
            log.debug("Skip pgvector extension creation. It may already exist or require elevated privileges.", e);
            return false;
        }
    }

    private String namedParameterInsertSql() {
        return NAMED_PARAMETER_INSERT_SQL.formatted(booksTable(), bookSequenceLiteral());
    }

    private String preparedStatementInsertSql() {
        return PREPARED_STATEMENT_INSERT_SQL.formatted(booksTable(), bookSequenceLiteral());
    }

    private String booksTable() {
        return schemaName() + ".books";
    }

    private String bookSequence() {
        return schemaName() + ".book_sequence";
    }

    private String bookSequenceLiteral() {
        return bookSequence();
    }

    private String schemaName() {
        String schema = environment.getProperty("book.performance.schema", "public");
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid book.performance.schema: " + schema);
        }
        return schema;
    }

    private static boolean hasExternalPostgreSqlUrl() {
        return externalPostgreSqlUrl() != null;
    }

    private static String externalPostgreSqlUrl() {
        String propertyValue = System.getProperty(EXTERNAL_DB_URL_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String environmentValue = System.getenv(EXTERNAL_DB_URL_ENV);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return null;
    }

    private static String externalValue(String environmentName, String defaultValue) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String postgresJdbcUrlWithPerformanceOptions(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl
                + separator
                + "rewriteBatchedInserts=true&stringtype=unspecified&currentSchema="
                + DEFAULT_SCHEMA
                + ",public";
    }

    @FunctionalInterface
    private interface BookChunkWriter {
        void write(List<Book> books) throws Exception;
    }

    @FunctionalInterface
    private interface SpringBatchWriterFactory {
        ItemWriter<Book> create() throws Exception;
    }

    private static class CsvImportTiming {
        private int rawReadCount;
        private int processedCount;
        private long parseProcessNanos;
        private long writeNanos;
        private long totalNanos;

        private int skippedCount() {
            return rawReadCount - processedCount;
        }

        private long parseProcessMillis() {
            return Duration.ofNanos(parseProcessNanos).toMillis();
        }

        private long writeMillis() {
            return Duration.ofNanos(writeNanos).toMillis();
        }

        private long totalMillis() {
            return Duration.ofNanos(totalNanos).toMillis();
        }

        private long batchOverheadMillis() {
            long overheadNanos = totalNanos - parseProcessNanos - writeNanos;
            return Duration.ofNanos(Math.max(0, overheadNanos)).toMillis();
        }
    }

    private static class TimedCsvBookRawDataReader implements ItemStreamReader<BookRawData> {

        private final CsvBookRawDataReader delegate;
        private final int rawItemLimit;
        private final CsvImportTiming timing;

        private TimedCsvBookRawDataReader(String bookFile, int rawItemLimit, CsvImportTiming timing) {
            this.delegate = new CsvBookRawDataReader(bookFile);
            this.rawItemLimit = rawItemLimit;
            this.timing = timing;
        }

        @Override
        public BookRawData read() throws Exception {
            if (rawItemLimit > 0 && timing.rawReadCount >= rawItemLimit) {
                return null;
            }

            long readStart = System.nanoTime();
            try {
                BookRawData rawData = delegate.read();
                if (rawData != null) {
                    timing.rawReadCount++;
                }
                return rawData;
            } finally {
                timing.parseProcessNanos += System.nanoTime() - readStart;
            }
        }

        @Override
        public void open(ExecutionContext executionContext) {
            delegate.open(executionContext);
        }

        @Override
        public void update(ExecutionContext executionContext) {
            delegate.update(executionContext);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static class BookBatchPreparedStatementSetter implements BatchPreparedStatementSetter {

        private final List<Book> books;

        private BookBatchPreparedStatementSetter(List<Book> books) {
            this.books = books;
        }

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Book book = books.get(i);
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthorName());
            ps.setString(4, book.getPublisherName());
            ps.setDate(5, toSqlDate(book.getFirstPublishDate()));
            ps.setBigDecimal(6, book.getPrice());
            ps.setString(7, book.getImageUrl());
            ps.setString(8, book.getBookContent());
            ps.setString(9, book.getCategory());
            ps.setString(10, book.getSubtitle());
            ps.setDate(11, toSqlDate(book.getEditionPublishDate()));
        }

        @Override
        public int getBatchSize() {
            return books.size();
        }

        private static Date toSqlDate(LocalDate date) {
            return date == null ? null : Date.valueOf(date);
        }
    }
}
