package com.nhnacademy.ailibraryteam5.core.book.scheduler;

import com.nhnacademy.ailibraryteam5.core.book.service.BookEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "scheduler.embedding.enabled",
        havingValue = "true"
)
public class BookEmbeddingScheduler {
    private final BookEmbeddingService bookEmbeddingService;
    private static final int BATCH_SIZE = 32;

    @Scheduled(fixedRate = 3000)
    public void scheduled() {
        try{
            int count = bookEmbeddingService.generateEmbedding(BATCH_SIZE);
            if(count > 0){
                log.info("{}권의 도서 임베딩 생성 완료",count);
            }
        }catch (Exception e){
            log.error("임베딩 생성 중 오류 발생",e);
        }
    }
}
