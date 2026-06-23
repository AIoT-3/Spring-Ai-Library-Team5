package com.nhnacademy.ailibrarycustom.batch.init.mapper;

import com.nhnacademy.ailibrarycustom.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarycustom.core.book.domain.Book;

import java.util.List;
import java.util.Objects;

public final class BookMapper {
    private BookMapper() {
        throw new IllegalStateException("Utility class");
    }

    public static Book toEntity(BookRawData rawData){
        if (Objects.isNull(rawData)){
            throw new IllegalArgumentException("BookRawData must not be null");
        }

        return new Book(
                rawData.getIsbn(),
                rawData.getCategory(),
                rawData.getTitle(),
                rawData.getAuthorName(),
                rawData.getPublisherName(),
                rawData.getFirstPublishDate(),
                rawData.getPrice(),
                rawData.getImageUrl(),
                rawData.getBookContent(),
                rawData.getSubtitle(),
                rawData.getEditionPublishDate()
        );
    }

    public static List<Book> toEntity(List<BookRawData> rawDataList){
        return rawDataList.stream().map(BookMapper::toEntity).toList();
    }

}
