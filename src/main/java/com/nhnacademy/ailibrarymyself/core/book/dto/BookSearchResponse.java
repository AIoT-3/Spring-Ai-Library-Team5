package com.nhnacademy.ailibrarymyself.core.book.dto;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class BookSearchResponse {
    private Long id;
    private String isbn;
    private String title;
    private String authorName;
    private String publisherName;
    private BigDecimal price;
    private LocalDate editionPublishDate;
    private String imageUrl;
    private String bookContent;
    private String category;

    public BookSearchResponse(
            Long id,
            String isbn,
            String title,
            String authorName,
            String publisherName,
            BigDecimal price,
            LocalDate editionPublishDate,
            String imageUrl,
            String bookContent,
            String category) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.price = price;
        this.editionPublishDate = editionPublishDate;
        this.imageUrl = imageUrl;
        this.bookContent = bookContent;
        this.category = category;
    }
    public static BookSearchResponse from(Book book) {
        return new BookSearchResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthorName(),
                book.getPublisherName(),
                book.getPrice(),
                book.getEditionPublishDate(),
                book.getImageUrl(),
                book.getBookContent(),
                book.getCategory()
        );
    }
}
