package com.nhnacademy.ailibrarycustom.batch.init.event;

import com.nhnacademy.ailibrarycustom.batch.init.dto.BookRawData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class BookParsedEvent {
    private final BookRawData bookRawData;
}
