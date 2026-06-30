package com.nhnacademy.ailibraryteam5.core.book.rag.cache;

import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;

import java.util.List;

public record SemanticRagCacheEntry(
        String normalizedKeyword,
        float[] embedding,
        List<BookAiRecommendationResponse> recommend,
        long createdAt
) {
}
