package com.nhnacademy.ailibraryteam5.core.book.builder;

import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import org.springframework.stereotype.Component;

@Component
public class BookEmbeddingTextBuilder {

    private static final int MAX_CONTENT_LENGTH = 500;

    public String build(Book book) {
        return """
                [제목] %s
                [부제] %s
                [저자] %s
                [출판사] %s
                [분류] %s
                [소개] %s
                """.formatted(
                safe(book.getTitle()),
                safe(book.getSubtitle()),
                safe(book.getAuthorName()),
                safe(book.getPublisherName()),
                safe(book.getCategory()),
                truncate(book.getBookContent(), MAX_CONTENT_LENGTH)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
