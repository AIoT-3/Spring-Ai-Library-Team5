package com.nhnacademy.library.batch.init.event;

import com.nhnacademy.library.batch.init.dto.BookRawData;
import lombok.Getter;

@Getter
public class BookParsedEvent {
    private final BookRawData bookRawData;

    public BookParsedEvent(BookRawData bookRawData){
        this.bookRawData = bookRawData;
    }
}
