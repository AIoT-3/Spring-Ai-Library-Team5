package com.nhnacademy.ailibrarycustom.core.review.service;

import com.nhnacademy.ailibrarycustom.core.book.domain.Book;
import com.nhnacademy.ailibrarycustom.core.book.exception.BookNotFoundException;
import com.nhnacademy.ailibrarycustom.core.book.repository.BookRepository;
import com.nhnacademy.ailibrarycustom.core.review.domain.BookReview;
import com.nhnacademy.ailibrarycustom.core.review.domain.BookReviewSummary;
import com.nhnacademy.ailibrarycustom.core.review.dto.BookReviewStatisticsDto;
import com.nhnacademy.ailibrarycustom.core.review.dto.ReviewCreateRequest;
import com.nhnacademy.ailibrarycustom.core.review.dto.ReviewResponse;
import com.nhnacademy.ailibrarycustom.core.review.event.ReviewCreatedEvent;
import com.nhnacademy.ailibrarycustom.core.review.event.ReviewSummaryEvent;
import com.nhnacademy.ailibrarycustom.core.review.repository.review.BookReviewRepository;
import com.nhnacademy.ailibrarycustom.core.review.repository.summary.BookReviewSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final BookReviewRepository bookReviewRepository;
    private final BookRepository bookRepository;
    private final BookReviewSummaryRepository bookReviewSummaryRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 리뷰 등록
    @Transactional
    public Long createReview(Long bookId, ReviewCreateRequest request){
        Book book = bookRepository.findBookById(bookId)
                .orElseThrow( () -> new BookNotFoundException(bookId));

        BookReview review = new BookReview(book, request.content(), request.rating());

        BookReview savedReview = bookReviewRepository.save(review);

        eventPublisher.publishEvent(new ReviewCreatedEvent(bookId));

        return savedReview.getId();
    }

    // 평점/별점 개수 실시간 수학 계산 갱신
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 독립된 DB 트랜잭션
    public void updateReviewSummary(Long bookId){
        // AI 요약글을 보존하고 통계만 안전하게 볼 수 있음
        BookReviewStatisticsDto statDto = bookReviewSummaryRepository.calculateStatistics(bookId)
                .orElseThrow( () -> new IllegalStateException("리뷰 통계를 집계할 수 없습니다. 책 ID : " + bookId));

        // 기존 요약한 엔티티가 있으면 가져오고, 없으면 새로 생성
        BookReviewSummary summary = bookReviewSummaryRepository.findById(bookId)
                .orElseGet(() -> new BookReviewSummary(bookId));

        summary.updateStat(
                statDto.reviewCount(),
                BigDecimal.valueOf(statDto.averageRating()),
                statDto.rating1Count(),
                statDto.rating2Count(),
                statDto.rating3Count(),
                statDto.rating4Count(),
                statDto.rating5Count(),
                statDto.lastReviewedAt().toLocalDateTime()
        );

        if(summary.getVersion() == 0){
            bookReviewSummaryRepository.save(summary);
        }

        eventPublisher.publishEvent(new ReviewSummaryEvent(bookId));
    }

    // 리뷰 페이징 조회
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviews(Long bookId, Pageable pageable){
        return bookReviewRepository.findAllByBookIdOrderByCreatedAtDesc(bookId, pageable)
                .map(ReviewResponse::from);
    }



}




















