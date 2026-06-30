package com.nhnacademy.ailibraryteam5.core.review.service.messaging;

public record ReviewSummaryMessage(Long bookId, long enqueueTime) {
    public ReviewSummaryMessage(Long bookId){
        this(bookId, System.currentTimeMillis());
    }
}
