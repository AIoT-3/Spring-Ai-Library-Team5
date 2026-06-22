package com.nhnacademy.library.parser;

import com.nhnacademy.library.batch.init.event.BookParsedEvent;
import com.nhnacademy.library.batch.init.event.BookParsingCompleteEvent;
import com.nhnacademy.library.batch.init.parser.CsvBookParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@RecordApplicationEvents
class CcvBookParserTest {
    @Autowired
    private CsvBookParser csvBookParser;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("CSV 파일을 읽어서 정상적으로 이벤트를 발행하는지 테스트")
    void parseCsvAndPublishEventsTest() throws Exception {
        // given
        // src/test/resources/test-books.csv 경로 지정
        String filePath = "data/init/BOOK_DB_202112.csv";

        // when
        csvBookParser.parse(filePath);

        // then
        // 1. BookParsedEvent가 총 2번 발행되었는지 검증
        long parsedEventCount = events.stream(BookParsedEvent.class).count();
        assertThat(parsedEventCount).isEqualTo(157118);

        // 2. 첫 번째로 파싱된 데이터 내용 확인
        BookParsedEvent firstEvent = events.stream(BookParsedEvent.class)
                .findFirst()
                .orElseThrow();
        assertThat(firstEvent.getBookRawData().getId()).isEqualTo(6352228);
        assertThat(firstEvent.getBookRawData().getAuthorName()).isEqualTo("이용신 (지은이)");

        // 3. 완료 이벤트(BookParsingCompleteEvent)가 1번 발행되었는지 검증
        long completeEventCount = events.stream(BookParsingCompleteEvent.class).count();
        assertThat(completeEventCount).isEqualTo(1);
    }
}
