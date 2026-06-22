package com.nhnacademy.library.batch.init.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BookRawData {

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

}
