package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
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
@ActiveProfiles(resolver = BookPerformanceActiveProfilesResolver.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.data.jpa.repositories.enabled=false"
})
@EnabledIfSystemProperty(
        named = "book.writer.performance",
        matches = "true",
        disabledReason = "Run with -Dbook.writer.performance=true to execute this performance test."
)
class BookWriterStrategyComparisonPerformanceTest {

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

    @BeforeEach
    void setUp() {
        recreateBooksTable();
    }

    @Test
    void compareBookWriterStrategies() throws Exception {
        int itemCount = Integer.getInteger("book.writer.performance.items", 10_000);
        int chunkSize = Integer.getInteger("book.writer.performance.chunk-size", 1_000);

        runAndVerify("JPA saveAll", itemCount, chunkSize,
                books -> {
                    SimpleJpaRepository<Book, Long> repository = new SimpleJpaRepository<>(Book.class, entityManager);
                    repository.saveAll(books);
                    repository.flush();
                    entityManager.clear();
                });

        runAndVerify("Simple chunk saveAll", itemCount, chunkSize,
                books -> {
                    for (int from = 0; from < books.size(); from += chunkSize) {
                        int to = Math.min(from + chunkSize, books.size());
                        SimpleJpaRepository<Book, Long> repository = new SimpleJpaRepository<>(Book.class, entityManager);
                        repository.saveAll(books.subList(from, to));
                        repository.flush();
                        entityManager.clear();
                    }
                });

        runAndVerify("JdbcTemplate.batchUpdate", itemCount, chunkSize,
                books -> {
                    for (int from = 0; from < books.size(); from += chunkSize) {
                        int to = Math.min(from + chunkSize, books.size());
                        List<Book> chunk = books.subList(from, to);
                        jdbcTemplate.batchUpdate(preparedStatementInsertSql(), new BookBatchPreparedStatementSetter(chunk));
                    }
                });

        JdbcBatchItemWriter<Book> writer = new JdbcBatchItemWriterBuilder<Book>()
                .dataSource(dataSource)
                .sql(namedParameterInsertSql())
                .beanMapped()
                .build();
        writer.afterPropertiesSet();

        runAndVerify("JdbcBatchItemWriter", itemCount, chunkSize,
                books -> {
                    for (int from = 0; from < books.size(); from += chunkSize) {
                        int to = Math.min(from + chunkSize, books.size());
                        writer.write(new Chunk<>(books.subList(from, to)));
                    }
                });
    }

    private void runAndVerify(
            String strategyName,
            int itemCount,
            int chunkSize,
            BookInsertStrategy strategy
    ) throws Exception {
        recreateBooksTable();
        List<Book> books = createBooks(itemCount);

        long start = System.nanoTime();
        strategy.insert(books);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        Integer savedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + booksTable(), Integer.class);

        log.info(
                "Book writer strategy performance result. strategy={}, items={}, chunkSize={}, elapsedMillis={}, elapsedSeconds={}",
                strategyName,
                itemCount,
                chunkSize,
                elapsedMillis,
                elapsedMillis / 1000.0
        );

        assertThat(savedCount)
                .as("%s saved row count", strategyName)
                .isEqualTo(itemCount);
    }

    private void recreateBooksTable() {
        entityManager.clear();
        createVectorExtensionIfPostgreSql();
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName());
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + booksTable());
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS " + bookSequence());
        jdbcTemplate.execute("CREATE SEQUENCE " + bookSequence() + " START WITH 1 INCREMENT BY 1000");
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
                    embedding vector
                )
                """.formatted(booksTable()));
    }

    private void createVectorExtensionIfPostgreSql() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName()
        );
        if ("PostgreSQL".equals(databaseProductName)) {
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            } catch (DataAccessException e) {
                log.debug("Skip pgvector extension creation. It may already exist or require elevated privileges.", e);
            }
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

    private static List<Book> createBooks(int count) {
        List<Book> books = new ArrayList<>(count);
        LocalDate publishDate = LocalDate.of(2024, 1, 1);

        for (int i = 0; i < count; i++) {
            books.add(new Book(
                    "978" + String.format("%010d", i),
                    "테스트 도서 " + i,
                    "테스트 저자",
                    "테스트 출판사",
                    publishDate,
                    BigDecimal.valueOf(10_000 + i),
                    "https://example.com/book-" + i + ".jpg",
                    "성능 테스트용 도서 설명 " + i,
                    "000",
                    "부제 " + i,
                    publishDate.plusDays(i % 365)
            ));
        }

        return books;
    }

    @FunctionalInterface
    private interface BookInsertStrategy {
        void insert(List<Book> books) throws Exception;
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
