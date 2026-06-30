package com.nhnacademy.ailibraryteam5.core.book.rag.dto;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;

import java.util.List;

public record BookRagResult(
        List<BookSearchResponse> books,
        List<BookAiRecommendationResponse> recommend,
        long totalElements,
        boolean aiAvailable
) {
    public BookRagResult(List<BookSearchResponse> books, List<BookAiRecommendationResponse> recommend, boolean aiAvailable) {
        this(books, recommend, books == null ? 0 : books.size(), aiAvailable);
    }

    public static BookRagResult fallback(List<BookSearchResponse> books, long totalElements){
        return new BookRagResult(books, List.of(), totalElements, false);
    }

    public static BookRagResult fallback(List<BookSearchResponse> books){
        return fallback(books, books == null ? 0 : books.size());
    }
}
