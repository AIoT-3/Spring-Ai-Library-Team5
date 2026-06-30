package com.nhnacademy.ailibrarycustom.core.review.repository.review;

import com.nhnacademy.ailibrarycustom.core.review.domain.BookReview;

import java.util.List;

public interface BookReviewRepositoryCustom {
    List<BookReview> findNewReviewsAfterId(Long bookId, Long lastSummarizedCount);
}
