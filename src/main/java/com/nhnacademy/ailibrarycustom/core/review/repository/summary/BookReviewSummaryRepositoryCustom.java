package com.nhnacademy.ailibrarycustom.core.review.repository.summary;

import com.nhnacademy.ailibrarycustom.core.review.dto.BookReviewStatisticsDto;

import java.util.Optional;

public interface BookReviewSummaryRepositoryCustom {
    Optional<BookReviewStatisticsDto> calculateStatistics(Long bookId);
}
