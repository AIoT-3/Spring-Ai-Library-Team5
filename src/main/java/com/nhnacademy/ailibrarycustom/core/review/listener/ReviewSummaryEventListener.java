package com.nhnacademy.ailibrarycustom.core.review.listener;

import com.nhnacademy.ailibrarycustom.core.review.event.ReviewSummaryEvent;
import com.nhnacademy.ailibrarycustom.core.review.service.messaging.ReviewSummaryQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewSummaryEventListener {
    private final ReviewSummaryQueueProducer queueProducer;

    @Async("taskExecutor")
    @EventListener
    public void handle(ReviewSummaryEvent event){

        boolean enqueued = queueProducer.enqueue(event.bookId());

        if(enqueued){
            log.info("도서 요약 작업이 RabbitMQ 큐에 등록 : {}", event.bookId());
        }else {
            log.info("이미 존재함 : bookId={}", event.bookId());
        }
    }
}
