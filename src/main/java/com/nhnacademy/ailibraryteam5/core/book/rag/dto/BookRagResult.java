package com.nhnacademy.ailibraryteam5.core.book.rag.dto;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;

import java.util.List;

public record BookRagResult(
        List<BookSearchResponse> books,
        List<BookAiRecommendationResponse> recommend,
        boolean aiAvailable
) {
    public static BookRagResult fallback(List<BookSearchResponse> books){
        return new BookRagResult(books, List.of(), false);
    }
}
