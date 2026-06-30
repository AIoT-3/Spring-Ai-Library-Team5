package com.nhnacademy.ailibraryteam5.core.review.repository.summary;

import com.nhnacademy.ailibraryteam5.core.review.dto.BookReviewStatisticsDto;

import java.util.Optional;

public interface BookReviewSummaryRepositoryCustom {
    Optional<BookReviewStatisticsDto> calculateStatistics(Long bookId);
}
