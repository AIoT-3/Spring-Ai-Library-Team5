package com.nhnacademy.ailibrarymyself.batch.init;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

@Component
public class BookInitPerformanceMetrics {

    private final LongAdder dbWriteNanos = new LongAdder();
    private final LongAdder dbWriteItems = new LongAdder();
    private final LongAdder dbWriteChunks = new LongAdder();

    public void reset() {
        dbWriteNanos.reset();
        dbWriteItems.reset();
        dbWriteChunks.reset();
    }

    public void recordDbWrite(int itemCount, long elapsedNanos) {
        dbWriteItems.add(itemCount);
        dbWriteChunks.increment();
        dbWriteNanos.add(elapsedNanos);
    }

    public long dbWriteMillis() {
        return Duration.ofNanos(dbWriteNanos.sum()).toMillis();
    }

    public long dbWriteItems() {
        return dbWriteItems.sum();
    }

    public long dbWriteChunks() {
        return dbWriteChunks.sum();
    }
}
