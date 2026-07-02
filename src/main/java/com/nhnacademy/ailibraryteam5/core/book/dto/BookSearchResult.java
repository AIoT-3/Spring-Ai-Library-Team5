package com.nhnacademy.ailibraryteam5.core.book.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

@Getter
@RequiredArgsConstructor
@Builder
public class BookSearchResult {

    private final Page<BookSearchResponse> books;

    public static BookSearchResult of(Page<BookSearchResponse> books) {
        return new BookSearchResult(books);
    }
}
