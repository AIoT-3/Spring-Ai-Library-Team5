package com.nhnacademy.ailibrarycustom.core.book.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BookSearchResponse {
    private Long id;
    private String isbn;
    private String title;
    private String authorName;
    private String publisherName;
    private String bookContent;
    private String imageUrl;

    @QueryProjection
    public BookSearchResponse(
            Long id,
            String isbn,
            String title,
            String authorName,
            String publisherName,
            String bookContent,
            String imageUrl
    ){
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.bookContent = bookContent;
        this.imageUrl = imageUrl;
    }
}
