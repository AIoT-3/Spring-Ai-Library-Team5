package com.nhnacademy.ailibrarycustom.core.review.service.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryQueueProducer {
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.review-summary}")
    private String routingKey;

    @Value("${app.review.dedup-window-ms}")
    private long dedupWindowMs;

    // 요약 중인 책의 중복 요약 방지 실시간 대기 명단
    private final Map<Long, Long> pendingBooks = new ConcurrentHashMap<>();

    @Value("${rabbitmq.queue.review-summary}")
    private String queueName;

    // RabbitMQ로 메시지 발행
    public boolean enqueue(Long bookId){
        long now = System.currentTimeMillis();
        Long lastEnqueueTime = pendingBooks.get(bookId);

        if(lastEnqueueTime != null && (now - lastEnqueueTime) < dedupWindowMs){ // 5초 이내면 false
            return false;
        }

        try{
            ReviewSummaryMessage message = new ReviewSummaryMessage(bookId);
            // 메시지 발행
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            pendingBooks.put(bookId, now);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    public void removePending(Long bookId){
        pendingBooks.remove(bookId);
    }
}
