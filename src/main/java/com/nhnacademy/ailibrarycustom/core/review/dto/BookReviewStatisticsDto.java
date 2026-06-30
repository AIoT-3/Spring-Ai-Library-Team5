package com.nhnacademy.ailibrarycustom.core.review.dto;

import com.querydsl.core.annotations.QueryProjection;

import java.time.OffsetDateTime;


public record BookReviewStatisticsDto(
        Long reviewCount,// 총 리뷰 수
        Double averageRating, // 평균 평점

        // 별점별 개수
        Integer rating1Count,
        Integer rating2Count,
        Integer rating3Count,
        Integer rating4Count,
        Integer rating5Count,

        // 가장 최근 리뷰 등록 시간
        OffsetDateTime lastReviewedAt
){
    @QueryProjection
    public BookReviewStatisticsDto {}

}
