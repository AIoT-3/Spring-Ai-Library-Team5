package com.nhnacademy.ailibraryteam5.core.book.rag.dto;

public record BookAiRecommendationResponse(
        Long id,
        String title,
        String why,
        Double similarity,
        Double rrfScore,
        Double recommendationScore
) {
    public BookAiRecommendationResponse{
        if(id == null){
            throw new IllegalArgumentException("BookAiRecommendationResponse: id is null");
        }
    }

    public String recommendationScorePercent() {
        if (recommendationScore == null) {
            return null;
        }
        return String.format("%.1f%%", recommendationScore);
    }

    public String similarityPercent() {
        if (similarity == null) {
            return null;
        }
        return String.format("%.1f%%", similarity * 100);
    }
}
