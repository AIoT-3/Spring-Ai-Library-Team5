package com.nhnacademy.library.core.book.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class BookSearchResult {
    private Page<BookSearchResponse> books;
}
