package com.nhnacademy.ailibraryteam5.core.review.exception;

public class BookReviewSummaryNotFoundException extends RuntimeException {
    public BookReviewSummaryNotFoundException(Long bookId) {
        super("해당 도서의 리뷰 요약 정보가 존재하지 않습니다. bookId: " + bookId);
    }
}
