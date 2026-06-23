package com.nhnacademy.ailibrarymyself.batch.init.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookRawData{
    private Long id;
    private String isbn;
    private String title;
    private String authorName;
    private String publisherName;
    private LocalDate firstPublishDate;
    private BigDecimal price;
    private String imageUrl;
    private String bookContent;
    private String category;
    private String subtitle;
    private LocalDate editionPublishDate;
}
