package com.nhnacademy.ailibrarycustom.batch.init.event.listener;

import com.nhnacademy.ailibrarycustom.AiLibraryCustomApplication;
import com.nhnacademy.ailibrarycustom.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarycustom.batch.init.event.BookParsedEvent;
import com.nhnacademy.ailibrarycustom.batch.init.event.BookParsingCompleteEvent;
import com.nhnacademy.ailibrarycustom.batch.init.parser.CsvBookParser;
import com.nhnacademy.ailibrarycustom.batch.init.properties.InitProperties;
import com.nhnacademy.ailibrarycustom.batch.init.service.BookBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVParser;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class BookParsingEventListener {
    private final InitProperties initProperties;
    private final CsvBookParser csvBookParser;
    private final List<BookRawData> buffer = new ArrayList<>();
    private final BookBatchService bookBatchService;


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(){
        if(initProperties.isEnable()){
            try{
                csvBookParser.parse();
            }catch (UnsupportedEncodingException e){
                throw new RuntimeException(e);
            }catch (IOException e){
            }
        }
    }

    @EventListener
    public void onBookParsed(BookParsedEvent event){
        buffer.add(event.getBookRawData());
    }

    @EventListener
    public void onParsingCompleted(BookParsingCompleteEvent event){
        bookBatchService.initializeBooks(buffer, initProperties.getBatchSize());
    }
}
