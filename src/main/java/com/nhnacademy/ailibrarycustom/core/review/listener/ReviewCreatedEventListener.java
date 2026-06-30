package com.nhnacademy.ailibrarycustom.core.review.listener;

import com.nhnacademy.ailibrarycustom.core.review.event.ReviewCreatedEvent;
import com.nhnacademy.ailibrarycustom.core.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCreatedEventListener {
    private final ReviewService reviewService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewCreatedEvent(ReviewCreatedEvent event){
        log.info("리뷰 생성 이벤트 수신 : booId={}", event.bookId());
        reviewService.updateReviewSummary(event.bookId());
    }
}
