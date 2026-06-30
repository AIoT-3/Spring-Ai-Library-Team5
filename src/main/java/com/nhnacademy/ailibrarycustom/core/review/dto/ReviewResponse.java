package com.nhnacademy.ailibrarycustom.core.review.dto;

import com.nhnacademy.ailibrarycustom.core.review.domain.BookReview;

import java.time.OffsetDateTime;

public record ReviewResponse(
        Long id,
        String content,
        Integer rating,
        OffsetDateTime createdAt
) {
    public static ReviewResponse from(BookReview review){
        return new ReviewResponse(
                review.getId(),
                review.getContent(),
                review.getRating(),
                review.getCreatedAt()
        );
    }
}
