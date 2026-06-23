package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookRawDataProcessor implements ItemProcessor<BookRawData, Book> {

    @Override
    public Book process(BookRawData item) {
        trimTextFields(item);

        if (isBlank(item.getIsbn())) {
            log.debug("skip book raw data. reason=missing isbn, id={}, title={}", item.getId(), item.getTitle());
            return null;
        }

        if (isBlank(item.getTitle())) {
            log.debug("skip book raw data. reason=missing title, id={}, isbn={}", item.getId(), item.getIsbn());
            return null;
        }

        return toEntity(item);
    }

    private void trimTextFields(BookRawData item) {
        item.setIsbn(trimToNull(item.getIsbn()));
        item.setTitle(trimToNull(item.getTitle()));
        item.setAuthorName(trimToNull(item.getAuthorName()));
        item.setPublisherName(trimToNull(item.getPublisherName()));
        item.setImageUrl(trimToNull(item.getImageUrl()));
        item.setBookContent(trimToNull(item.getBookContent()));
        item.setCategory(trimToNull(item.getCategory()));
        item.setSubtitle(trimToNull(item.getSubtitle()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Book toEntity(BookRawData item) {
        return new Book(
                item.getIsbn(),
                item.getTitle(),
                item.getAuthorName(),
                item.getPublisherName(),
                item.getFirstPublishDate(),
                item.getPrice(),
                item.getImageUrl(),
                item.getBookContent(),
                item.getCategory(),
                item.getSubtitle(),
                item.getEditionPublishDate()
        );
    }
}
