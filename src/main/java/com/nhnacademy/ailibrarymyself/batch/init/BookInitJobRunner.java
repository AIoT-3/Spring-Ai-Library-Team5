package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.properties.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookInitJobRunner implements ApplicationRunner {

    private final InitProperties initProperties;
    private final JobLauncher jobLauncher;
    private final Job bookInitJob;
    private final JdbcTemplate jdbcTemplate;
    private final BookInitPerformanceMetrics metrics;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!initProperties.isEnable()) {
            log.info("book init job skipped. init.enable=false");
            return;
        }

        resetBooksIfRequested();
        metrics.reset();

        log.info(
                "book init job launching. file={}, batchSize={}, resetBeforeLoad={}",
                initProperties.getBookFile(),
                initProperties.getBatchSize(),
                initProperties.isResetBeforeLoad()
        );

        long startedAt = System.nanoTime();
        JobExecution jobExecution = jobLauncher.run(
                bookInitJob,
                new JobParametersBuilder()
                        .addLong("requestedAt", System.currentTimeMillis())
                        .toJobParameters()
        );
        long totalMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        logPerformance(jobExecution, totalMillis);
    }

    private void resetBooksIfRequested() {
        if (!initProperties.isResetBeforeLoad()) {
            return;
        }

        log.warn("book init reset enabled. truncate books and restart public.book_sequence before loading.");
        jdbcTemplate.execute("TRUNCATE TABLE books");
        jdbcTemplate.execute("ALTER SEQUENCE public.book_sequence RESTART WITH 1");
        log.warn("book init reset completed.");
    }

    private void logPerformance(JobExecution jobExecution, long totalMillis) {
        long readCount = 0;
        long writeCount = 0;
        long filterCount = 0;
        long skipCount = 0;
        long commitCount = 0;

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            readCount += stepExecution.getReadCount();
            writeCount += stepExecution.getWriteCount();
            filterCount += stepExecution.getFilterCount();
            skipCount += stepExecution.getSkipCount();
            commitCount += stepExecution.getCommitCount();
        }

        log.info(
                "book init job finished. status={}, file={}, batchSize={}, readCount={}, writeCount={}, filterCount={}, skipCount={}, commitCount={}, dbWriteItems={}, dbWriteChunks={}, dbWriteMillis={}, totalMillis={}, totalSeconds={}",
                jobExecution.getStatus(),
                initProperties.getBookFile(),
                initProperties.getBatchSize(),
                readCount,
                writeCount,
                filterCount,
                skipCount,
                commitCount,
                metrics.dbWriteItems(),
                metrics.dbWriteChunks(),
                metrics.dbWriteMillis(),
                totalMillis,
                totalMillis / 1000.0
        );
    }
}
