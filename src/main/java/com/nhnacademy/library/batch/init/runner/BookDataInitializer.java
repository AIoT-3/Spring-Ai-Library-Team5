package com.nhnacademy.library.batch.init.runner;

import com.nhnacademy.library.batch.init.parser.CsvBookParser;
import com.nhnacademy.library.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookDataInitializer implements ApplicationRunner {
    private final CsvBookParser csvBookParser;
    private final BookRepository bookRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 어플리케이션 구동 완료 - csv 데이터 적재 시작 ===");

        if(bookRepository.count() > 0){
            log.info("이미 데이터베이스에 도서 데이터가 존재하므로 적재를 건너뜁니다.");
            return;
        }

        String filePath = "data/init/BOOK_DB_202112.csv";

        try{
            csvBookParser.parse(filePath);
            log.info("=== csv 데이터 적재 완료 ===");
        }catch (Exception e){
            log.error("!!! csv 데이터 적재중 오류 발생 !!!");

        }

    }
}
