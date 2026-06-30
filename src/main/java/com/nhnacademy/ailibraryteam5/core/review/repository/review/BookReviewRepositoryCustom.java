package com.nhnacademy.ailibraryteam5.core.review.repository.review;

import com.nhnacademy.ailibraryteam5.core.review.domain.BookReview;

import java.util.List;

public interface BookReviewRepositoryCustom {
    List<BookReview> findNewReviewsAfterId(Long bookId, Long lastSummarizedCount);
}
