package com.nhnacademy.ailibraryteam5.core.review.service.messaging;

import com.nhnacademy.ailibraryteam5.core.review.service.ReviewAiSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryQueueConsumer {
    private final ReviewAiSummaryService aiSummaryService;
    private final ReviewSummaryQueueProducer queueProducer;

    @RabbitListener(
            queues =  "${rabbitmq.queue.review-summary}",
            concurrency = "${rabbitmq.concurrency.review-summary:3-5}"
    )
    public void processTask(ReviewSummaryMessage task){
        Long bookId = task.bookId();
        log.info("RabbitMQ 수신 : booKId = {}", bookId);
        try{
            aiSummaryService.generateSummary(bookId);
        }catch (Exception e){
            log.error("도서 AI 요약 처리 중 오류 발생 : bookId = {}, error={}", bookId, e.getMessage(), e);
        }finally {
            queueProducer.removePending(bookId);
        }
    }
}
