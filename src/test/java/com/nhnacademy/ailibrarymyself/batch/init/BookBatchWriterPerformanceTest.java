package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@JdbcTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookBatchWriterPerformanceTest {

    private static final String INSERT_SQL = """
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

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS books");
        jdbcTemplate.execute("DROP SEQUENCE IF EXISTS public.book_sequence");
        jdbcTemplate.execute("CREATE SEQUENCE public.book_sequence START WITH 1 INCREMENT BY 1000");
        jdbcTemplate.execute("""
                CREATE TABLE books (
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
                """);
    }

    @Test
    void logJdbcBatchItemWriterElapsedTime() throws Exception {
        assumeTrue(Boolean.getBoolean("book.writer.performance"),
                "Run with -Dbook.writer.performance=true to execute this performance test.");

        int itemCount = Integer.getInteger("book.writer.performance.items", 10_000);
        int chunkSize = Integer.getInteger("book.writer.performance.chunk-size", 1_000);

        JdbcBatchItemWriter<Book> writer = new JdbcBatchItemWriterBuilder<Book>()
                .dataSource(dataSource)
                .sql(INSERT_SQL)
                .beanMapped()
                .build();
        writer.afterPropertiesSet();

        List<Book> books = createBooks(itemCount);

        long start = System.nanoTime();
        for (int from = 0; from < books.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, books.size());
            writer.write(new Chunk<>(books.subList(from, to)));
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        Integer savedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM books", Integer.class);

        log.info(
                "JdbcBatchItemWriter performance result. items={}, chunkSize={}, elapsedMillis={}, elapsedSeconds={}",
                itemCount,
                chunkSize,
                elapsedMillis,
                elapsedMillis / 1000.0
        );

        assertThat(savedCount).isEqualTo(itemCount);
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
}
