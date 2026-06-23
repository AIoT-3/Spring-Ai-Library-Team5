package com.nhnacademy.ailibrarymyself.core.book.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "books",
        indexes = {
            @Index(name = "idx_book_isbn", columnList = "isbn", unique = true)
        }
)

@Getter
@NoArgsConstructor
public class Book {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "book_sequence_generator"
    )

    @SequenceGenerator(
            name = "book_sequence_generator",
            sequenceName = "public.book_sequence",
            allocationSize = 1000
    )
    @Column(name = "id")
    private Long id;

    @Column(name = "isbn", length = 20, unique = true, nullable = false)
    private String isbn;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "author_name", length = 1000)
    private String authorName;

    @Column(name = "publisher_name", length = 255)
    private String publisherName;

    @Column(name = "first_publish_date")
    private LocalDate firstPublishDate;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "book_content", columnDefinition = "TEXT")
    private String bookContent;

    @Column(name = "category", length = 20, nullable = true)
    private String category;

    @Column(name = "subtitle", length = 500)
    private String subtitle;

    @Column(name = "edition_publish_date")
    private LocalDate editionPublishDate;

    @Column(name="created_at",updatable = false,nullable = false)
    private OffsetDateTime createdAt;

    @Column(name="updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "embedding", columnDefinition = "vector(1024)")
    @Array(length = 1024)
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Book(
            String isbn,
            String title,
            String authorName,
            String publisherName,
            LocalDate firstPublishDate,
            BigDecimal price,
            String imageUrl,
            String bookContent,
            String category,
            String subtitle,
            LocalDate editionPublishDate
    ) {
        this.isbn = isbn;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.firstPublishDate = firstPublishDate;
        this.price = price;
        this.imageUrl = imageUrl;
        this.bookContent = bookContent;
        this.category = category;
        this.subtitle = subtitle;
        this.editionPublishDate = editionPublishDate;
    }

}
