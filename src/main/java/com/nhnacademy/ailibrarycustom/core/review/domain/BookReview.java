package com.nhnacademy.ailibrarycustom.core.review.domain;

import com.nhnacademy.ailibrarycustom.core.book.domain.Book;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "book_reviews")
@Getter
@NoArgsConstructor
public class BookReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    // 내용
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 평점
    @Column(nullable = false)
    private Integer rating;

    // 현재 시간
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public BookReview(Book book, String content, Integer rating) {
        this.book = book;
        this.content = content;
        this.rating = rating;
        this.createdAt = OffsetDateTime.now();
    }
}
