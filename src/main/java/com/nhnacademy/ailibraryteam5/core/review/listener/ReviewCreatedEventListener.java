package com.nhnacademy.ailibraryteam5.core.review.listener;

import com.nhnacademy.ailibraryteam5.core.review.event.ReviewCreatedEvent;
import com.nhnacademy.ailibraryteam5.core.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCreatedEventListener {
    private final ReviewService reviewService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewCreatedEvent(ReviewCreatedEvent event){
        log.info("리뷰 생성 이벤트 수신 : booId={}", event.bookId());
        reviewService.updateReviewSummary(event.bookId());
    }
}
