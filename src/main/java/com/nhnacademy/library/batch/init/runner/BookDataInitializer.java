package com.nhnacademy.library.batch.init.runner;

import com.nhnacademy.library.batch.init.parser.CsvBookParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookDataInitializer implements ApplicationRunner {
    private final CsvBookParser csvBookParser;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 어플리케이션 구동 완료 - csv 데이터 적재 시작 ===");
        String filePath = "data/init/BOOK_DB_202112.csv";

        try{
            csvBookParser.parse(filePath);
            log.info("=== csv 데이터 적재 완료 ===");
        }catch (Exception e){
            log.error("!!! csv 데이터 적재중 오류 발생 !!!");

        }
    }
}
