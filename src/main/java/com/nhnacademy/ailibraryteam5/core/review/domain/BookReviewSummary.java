package com.nhnacademy.ailibraryteam5.core.review.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "book_review_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookReviewSummary {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    // 총 리뷰 수 갱신 (11개 -> 12개)
    // 5개 미만이면 아직 요약할 만큼 리뷰가 안보였다 판단 그냥 종료
    @Column(nullable = false)
    private Long reviewCount = 0L;

    // 새로운 평균값으로 갱신 (4.56 -> 4.58)
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "rating_1_count", nullable = false)
    private Integer rating1Count = 0;

    @Column(name = "rating_2_count", nullable = false)
    private Integer rating2Count = 0;

    @Column(name = "rating_3_count", nullable = false)
    private Integer rating3Count = 0;

    @Column(name = "rating_4_count", nullable = false)
    private Integer rating4Count = 0;

    // 5점짜리 개수가 1개 늘어나므로 기존 값에서 +1 갱신
    @Column(name = "rating_5_count", nullable = false)
    private Integer rating5Count = 0;

    // 방금 쓴 리뷰의 생성 시각으로 최신화
    @Column
    private LocalDateTime lastReviewedAt;

    // < -------------------------------------------------- >

    // 요약 내용 업데이트 필요! true = 필요, false = 최신 상태
    @Column(nullable = false)
    private Boolean isSummaryDirty = true;

    // < -------------------------------------------------- >

    // AI가 요약할 때

    // true이면 이미 다른 스레드가 요약본 만드는 중이네 하고 종료
    @Column(name = "is_generating")
    private Boolean isGenerating = false;

    // 마지막 요약 당시의 리뷰 번호를 읽습니다.
    @Column(name = "last_summarized_count")
    private Long lastSummarizedCount = 0L;

    // 누적 요약 실행
    @Column(columnDefinition = "TEXT")
    private String reviewSummary;


    // < -------------------------------------------------- >

    // 마지막으로 바뀐 시각
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 데이터가 갱신되었으므로 버전 번호가 자동 +1 증가
    // 동시에 두 스레드가 덮어쓰려 할 때 오류를 감지해내는 안전장치 버전
    @Version
    private long version;

    // 최초 생성용 생성자
    public BookReviewSummary(Long bookId){
        this.bookId = bookId;
        this.updatedAt = LocalDateTime.now();
        this.isSummaryDirty = true;
    }

    // 통계치 실시간 업데이트
    public void updateStatistics(Long reviewCount, BigDecimal averageRating, Integer rating1Count,
                           Integer rating2Count, Integer rating3Count, Integer rating4Count, Integer rating5Count,
                           LocalDateTime lastReviewedAt){
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.rating1Count = rating1Count;
        this.rating2Count = rating2Count;
        this.rating3Count = rating3Count;
        this.rating4Count = rating4Count;
        this.rating5Count = rating5Count;
        this.lastReviewedAt = lastReviewedAt;
        this.isSummaryDirty = true;
        this.updatedAt = LocalDateTime.now();
    }

    // AI 요약글 완료 시 업데이트 메서드
    public void updateSummaryWithCount(String summary, long lastSummarizedCount){
        this.reviewSummary = summary;
        this.lastSummarizedCount = lastSummarizedCount;
        this.isSummaryDirty = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void setGenerating(boolean generating){
        this.isGenerating = generating;
    }

    public void setSummaryDirty(boolean summaryDirty){
        this.isSummaryDirty = summaryDirty;
    }


}
