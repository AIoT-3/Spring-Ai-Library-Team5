package com.nhnacademy.ailibraryteam5.core.review.dto;

import java.math.BigDecimal;

public record ReviewSummaryResponse(
        Long bookId,
        String summary,
        BigDecimal avgRating
) {
}
