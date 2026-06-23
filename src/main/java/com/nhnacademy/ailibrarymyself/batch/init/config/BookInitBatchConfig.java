package com.nhnacademy.ailibrarymyself.batch.init.config;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarymyself.batch.init.properties.InitProperties;
import com.nhnacademy.ailibrarymyself.batch.init.CsvBookRawDataReader;
import com.nhnacademy.ailibrarymyself.batch.init.BookInitPerformanceMetrics;
import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
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
            ItemStreamReader<BookRawData> bookRawDataReader,
            ItemProcessor<BookRawData, Book> bookRawDataProcessor,
            ItemWriter<Book> bookRawDataWriter
    ) {
        return new StepBuilder("bookInitStep", jobRepository)
                .<BookRawData, Book>chunk(initProperties.getBatchSize(), transactionManager)
                .reader(bookRawDataReader)
                .processor(bookRawDataProcessor)
                .writer(bookRawDataWriter)
                .listener(stepLoggingListener())
                .listener(chunkLoggingListener())
                .build();
    }

    @Bean
    public StepExecutionListener stepLoggingListener() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info(
                        "book init step started. stepName={}, file={}, batchSize={}",
                        stepExecution.getStepName(),
                        initProperties.getBookFile(),
                        initProperties.getBatchSize()
                );
            }

            @Override
            public org.springframework.batch.core.ExitStatus afterStep(StepExecution stepExecution) {
                log.info(
                        "book init step finished. stepName={}, status={}, readCount={}, writeCount={}, filterCount={}, skipCount={}, commitCount={}, rollbackCount={}",
                        stepExecution.getStepName(),
                        stepExecution.getStatus(),
                        stepExecution.getReadCount(),
                        stepExecution.getWriteCount(),
                        stepExecution.getFilterCount(),
                        stepExecution.getSkipCount(),
                        stepExecution.getCommitCount(),
                        stepExecution.getRollbackCount()
                );
                return stepExecution.getExitStatus();
            }
        };
    }

    @Bean
    public ChunkListener chunkLoggingListener() {
        return new ChunkListener() {
            @Override
            public void afterChunk(org.springframework.batch.core.scope.context.ChunkContext context) {
                StepExecution stepExecution = context.getStepContext().getStepExecution();
                long commitCount = stepExecution.getCommitCount();

                if (commitCount == 1 || commitCount % 10 == 0) {
                    log.info(
                            "book init chunk progress. commitCount={}, readCount={}, writeCount={}, filterCount={}, skipCount={}",
                            commitCount,
                            stepExecution.getReadCount(),
                            stepExecution.getWriteCount(),
                            stepExecution.getFilterCount(),
                            stepExecution.getSkipCount()
                    );
                }
            }
        };
    }

    @Bean
    @StepScope
    public ItemStreamReader<BookRawData> bookRawDataReader() {
        return new CsvBookRawDataReader(initProperties.getBookFile());
    }

    @Bean
    public ItemWriter<Book> bookRawDataWriter(
            DataSource dataSource,
            BookInitPerformanceMetrics metrics
    ) throws Exception {
        JdbcBatchItemWriter<Book> delegate = new JdbcBatchItemWriterBuilder<Book>()
                .dataSource(dataSource)
                .sql(BOOK_INSERT_SQL)
                .beanMapped()
                .build();
        delegate.afterPropertiesSet();

        return chunk -> {
            long start = System.nanoTime();
            try {
                delegate.write(chunk);
            } finally {
                long elapsedNanos = System.nanoTime() - start;
                metrics.recordDbWrite(chunk.size(), elapsedNanos);
                log.info(
                        "book init chunk written. itemCount={}, dbWriteMillis={}",
                        chunk.size(),
                        java.time.Duration.ofNanos(elapsedNanos).toMillis()
                );
            }
        };
    }
}
