package com.nhnacademy.ailibraryteam5.core.book.rag.dto;

public record BookAiRecommendationResponse(
        Long id,
        Integer relevance,
        String why
) {
    public BookAiRecommendationResponse{
        if(id == null){
            throw new IllegalArgumentException("BookAiRecommendationResponse: id is null");
        }
        if(relevance == null || relevance < 0 || relevance > 100) {
            throw new IllegalArgumentException("BookAiRecommendationResponse: relevance is null");
        }
    }
}
