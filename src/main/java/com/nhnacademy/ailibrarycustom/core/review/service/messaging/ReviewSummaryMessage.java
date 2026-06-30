package com.nhnacademy.ailibrarycustom.core.review.service.messaging;

public record ReviewSummaryMessage(Long bookId, long enqueueTime) {
    public ReviewSummaryMessage(Long bookId){
        this(bookId, System.currentTimeMillis());
    }
}
