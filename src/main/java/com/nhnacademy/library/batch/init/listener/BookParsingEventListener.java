package com.nhnacademy.library.batch.init.listener;

import com.nhnacademy.library.batch.init.dto.BookRawData;
import com.nhnacademy.library.batch.init.event.BookParsedEvent;
import com.nhnacademy.library.batch.init.event.BookParsingCompleteEvent;
import com.nhnacademy.library.batch.init.service.BookBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookParsingEventListener {
    private final List<BookRawData> buffer = new ArrayList<>();

    private final BookBatchService batchService;

    @EventListener
    public void onBookParsed(BookParsedEvent event){
        buffer.add(event.getBookRawData());
    }

    @EventListener
    public void onParsingCompleted(BookParsingCompleteEvent event){
        log.info("파싱 완료, 도서 {}권 저장 시작", buffer.size());

        batchService.initializeBooks(buffer);
        buffer.clear();

        log.info("도서 저장 완료");
    }
}
