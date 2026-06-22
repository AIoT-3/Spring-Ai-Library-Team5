package com.nhnacademy.library.core.book.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class BookSearchResponse {
    private Long id;
    private String isbn;
    private String volumeTitle;
    private String title;
    private String authorName;
    private String publisherName;
    private LocalDate firstPublishDate;
    private BigDecimal price;
    private String imageUrl;
    private String bookContent;
    private String subtitle;
    private LocalDate editionPublishDate;

    @QueryProjection
    public BookSearchResponse(
            Long id,
            String isbn,
            String volumeTitle,
            String title,
            String authorName,
            String publisherName,
            LocalDate firstPublishDate,
            BigDecimal price,
            String bookContent,
            String imageUrl,
            String subtitle,
            LocalDate editionPublishDate
    ) {
        this.id = id;
        this.isbn = isbn;
        this.volumeTitle = volumeTitle;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.firstPublishDate = firstPublishDate;
        this.price = price;
        this.bookContent = bookContent;
        this.imageUrl = imageUrl;
        this.subtitle = subtitle;
        this.editionPublishDate = editionPublishDate;
    }
}
