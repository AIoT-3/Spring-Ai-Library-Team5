package com.nhnacademy.library.core.book.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Table(name = "test_books")
@Entity()
@Getter
@Setter
@NoArgsConstructor
public class Book implements Persistable<Long> {

    @Id
    private Long id;

    @Column(name = "isbn", length = 20, unique = true, nullable = false)
    private String isbn;

    @Column(name = "volume_title", length = 255)
    private String volumeTitle;

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

    @Column(name = "subtitle", length = 500)
    private String subtitle;

    @Column(name = "edition_publish_date")
    private LocalDate editionPublishDate;

//    @Convert(converter = VectorConverter.class)
//    @Column(name = "embedding", columnDefinition = "vector(1024)")
//    @JdbcTypeCode(SqlTypes.VARCHAR)
//    private float[] embedding;
//
//    public void updateEmbedding(float[] embedding) {
//        this.embedding = embedding;
//    }

    @Transient
    private boolean isNew = true;

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Book(Long id, String isbn, String volumeTitle, String title,
                String authorName, String publisherName, LocalDate firstPublishDate,
                BigDecimal price, String imageUrl, String bookContent, String subtitle,
                LocalDate editionPublishDate){
        this.id = id;
        this.isbn = isbn;
        this.volumeTitle = volumeTitle;
        this.title = title;
        this.authorName = authorName;
        this.publisherName = publisherName;
        this.firstPublishDate = firstPublishDate;
        this.price = price;
        this.imageUrl = imageUrl;
        this.bookContent = bookContent;
        this.subtitle = subtitle;
        this.editionPublishDate = editionPublishDate;
    }
}
