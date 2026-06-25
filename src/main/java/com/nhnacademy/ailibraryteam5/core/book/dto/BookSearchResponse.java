package com.nhnacademy.ailibraryteam5.core.book.dto;


import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
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
    private Double similarity; // 벡터 검색 유사도 점수
    private Double rrfScore; // rrf 점수

    @Builder
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
            String category,
            Double similarity,
            Double rrfScore) {
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
        this.similarity = similarity;
        this.rrfScore = rrfScore;
    }
    public static BookSearchResponse from(Book book) {
        return BookSearchResponse.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .authorName(book.getAuthorName())
                .publisherName(book.getPublisherName())
                .price(book.getPrice())
                .editionPublishDate(book.getEditionPublishDate())
                .imageUrl(book.getImageUrl())
                .bookContent(book.getBookContent())
                .category(book.getCategory())
                .build();
    }
    public String getSimilarityPercent() {
        if (similarity == null) {
            return null;
        }
        return String.format("%.1f%%", similarity * 100);
    }
}
