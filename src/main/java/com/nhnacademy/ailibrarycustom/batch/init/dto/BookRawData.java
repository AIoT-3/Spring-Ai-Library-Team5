package com.nhnacademy.ailibrarycustom.batch.init.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;


@Data
@Getter
@NoArgsConstructor
public class BookRawData {
    /** 도서 ID */
    private Long id;
    /** ISBN (고유번호) */
    private String isbn;
    private String category;
    /** 도서 제목 */
    private String title;

    /** 저자명 */
    private String authorName;

    /** 출판사명 */
    private String publisherName;

    private LocalDate firstPublishDate;
    private BigDecimal price;

    /** 도서 내용 미리보기 */
    private String bookContent;

    /** 표지 이미지 URL */
    private String imageUrl;

    private String subtitle;
    private LocalDate editionPublishDate;

    public BookRawData(Long id, String isbn, String category, String title, String authorName, String publisherName,
                       LocalDate firstPublishDate, BigDecimal price, String bookContent,
                       String imageUrl, String subtitle, LocalDate editionPublishDate) {
        this.id = id;
        this.isbn = isbn;
        this.category = category;
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
