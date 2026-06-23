package com.nhnacademy.ailibrarycustom.batch.init.service;

import com.nhnacademy.ailibrarycustom.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarycustom.batch.init.mapper.BookMapper;
import com.nhnacademy.ailibrarycustom.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookBatchService {
    private final BookRepository bookRepository;

    @Transactional
    public void initializeBooks(List<BookRawData> buffer, int batchSize){
        log.info("도서 데이터 DB 적재를 시작합니다. Buffer size: {}, Batch Size: {}", buffer.size(), batchSize);
        log.info("배치 적재 전 기존에 있는 책들을 삭제합니다.");
        bookRepository.deleteAll();
        bookRepository.flush();

        for (int i = 0; i<buffer.size(); i+=batchSize){
            int endIndex = Math.min(i + batchSize, buffer.size());
            log.debug("배치 저장 구간 {} to {}", i, endIndex);
            List<BookRawData> batch = buffer.subList(i, endIndex);
            bookRepository.saveAll(BookMapper.toEntity(batch));
        }
        log.info("Book Initialization completed");
    }
}
