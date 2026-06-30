package com.nhnacademy.ailibraryteam5.init;

import com.nhnacademy.ailibraryteam5.init.parser.CsvBookParser;
import com.nhnacademy.ailibraryteam5.init.properties.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookInitRunner implements ApplicationRunner {
    private final InitProperties initProperties;
    private final CsvBookParser csvBookParser;

    @Override
    public void run(ApplicationArguments args) {
        if (!initProperties.isEnable()) {
            log.info("book init skipped. init.enable=false");
            return;
        }

        try {
            csvBookParser.parse();
        } catch (IOException e) {
            throw new IllegalStateException("도서 CSV 초기화에 실패했습니다.", e);
        }
    }
}
