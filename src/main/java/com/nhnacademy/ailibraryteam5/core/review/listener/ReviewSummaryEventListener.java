package com.nhnacademy.ailibraryteam5.core.review.listener;

import com.nhnacademy.ailibraryteam5.core.book.rag.service.SematicRagCacheService;
import com.nhnacademy.ailibraryteam5.core.review.event.ReviewAiSummaryUpdatedEvent;
import com.nhnacademy.ailibraryteam5.core.review.event.ReviewSummaryEvent;
import com.nhnacademy.ailibraryteam5.core.review.service.messaging.ReviewSummaryQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewSummaryEventListener {
    private final ReviewSummaryQueueProducer queueProducer;
    private final SematicRagCacheService sematicRagCacheService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReviewSummaryEvent event){

        boolean enqueued = queueProducer.enqueue(event.bookId());

        if(enqueued){
            log.info("도서 요약 작업이 RabbitMQ 큐에 등록 : {}", event.bookId());
        }else {
            log.info("이미 존재함 : bookId={}", event.bookId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateRagCache(ReviewAiSummaryUpdatedEvent event) {
        sematicRagCacheService.invalidateByBookId(event.bookId());
    }
}
