package com.nhnacademy.ailibraryteam5.core.review.service;

import com.nhnacademy.ailibraryteam5.core.review.domain.BookReview;
import com.nhnacademy.ailibraryteam5.core.review.domain.BookReviewSummary;
import com.nhnacademy.ailibraryteam5.core.review.exception.BookReviewSummaryNotFoundException;
import com.nhnacademy.ailibraryteam5.core.review.repository.review.BookReviewRepository;
import com.nhnacademy.ailibraryteam5.core.review.repository.summary.BookReviewSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAiSummaryService {
    @Value("${app.review.min-count-for-summary}")
    private int minReviewCountForSummary;

    private final BookReviewSummaryRepository bookReviewSummaryRepository;
    private final BookReviewRepository bookReviewRepository;
    private final ReviewAiSummarizer reviewAiSummarizer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateSummary(Long bookId){
        BookReviewSummary summary = bookReviewSummaryRepository.findById(bookId)
                .orElseThrow(()-> new BookReviewSummaryNotFoundException(bookId));

        // 최소 요약 조건 5개 이상 및 dirty 검사
        if(!shouldGenerateSummary(summary)){
            log.info("요약 조건 미달로 스킵합니다 : bookId={}, reviewCount={}. isDirty={}", bookId, summary.getReviewCount(), summary.getIsSummaryDirty());
            return;
        }

        // 다른 일꾼이 이미 요약하고 있는지 중복 실행 방지
        if(Boolean.TRUE.equals(summary.getIsGenerating())){
            log.info("이미 다른 스레드에서 요약을 작성 중: bookId={}", bookId);
            return;
        }

        try{
            // 요약 시작 락(Lock) 걸어 2차 안전장치
            summary.setGenerating(true);
            bookReviewSummaryRepository.saveAndFlush(summary);

            long reviewCount = summary.getReviewCount();
            long lastSummaryCount = summary.getLastSummarizedCount() != null ? summary.getLastSummarizedCount() : 0L;

            long threshold = reviewAiSummarizer.getReduceThreshold();
            boolean needsRebuild = (reviewCount - lastSummaryCount >= threshold || (lastSummaryCount / threshold != reviewCount / threshold));

            String newSummary;

            if(needsRebuild){
                List<String> reviewContents = bookReviewRepository.findAllByBookId(bookId)
                        .stream()
                        .map(BookReview::getContent)
                        .toList();
                newSummary = reviewAiSummarizer.summarizeReviews(reviewContents);
                summary.updateSummaryWithCount(newSummary, reviewCount);

            }else {
                List<BookReview> newReviews = bookReviewRepository.findNewReviewsAfterId(bookId, lastSummaryCount);
                if(!newReviews.isEmpty()){
                    List<String> newReviewContents = newReviews.stream().map(BookReview::getContent).toList();
                    newSummary = reviewAiSummarizer.summarizeIncremental(newReviewContents, summary.getReviewSummary());
                    summary.updateSummaryWithCount(newSummary, reviewCount);
                }else {
                    log.info("요약할 최신 리뷰가 존재하지 않습니다: bookId={}", bookId);
                    summary.setSummaryDirty(false);
                }
            }

        }catch (Exception e){
            log.error("도서 요약 작성 중 오류 발생 : bookId={}, error={}", bookId, e.getMessage(), e);
        }finally {
            // 에러가 나거나 성공해도 항상 작성이 끝났으므로 락(Lock)
            summary.setGenerating(false);
            bookReviewSummaryRepository.save(summary);
        }
    }

    private boolean shouldGenerateSummary(BookReviewSummary summary){
        return summary.getReviewCount() >= minReviewCountForSummary && summary.getIsSummaryDirty();
    }
}
