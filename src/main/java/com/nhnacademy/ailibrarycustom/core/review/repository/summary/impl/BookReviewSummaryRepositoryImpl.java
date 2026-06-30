package com.nhnacademy.ailibrarycustom.core.review.repository.summary.impl;

import com.nhnacademy.ailibrarycustom.core.review.domain.QBookReview;
import com.nhnacademy.ailibrarycustom.core.review.dto.BookReviewStatisticsDto;
import com.nhnacademy.ailibrarycustom.core.review.dto.QBookReviewStatisticsDto;
import com.nhnacademy.ailibrarycustom.core.review.repository.summary.BookReviewSummaryRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookReviewSummaryRepositoryImpl implements BookReviewSummaryRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;
    @Override
    public Optional<BookReviewStatisticsDto> calculateStatistics(Long bookId) {
        QBookReview qBookReview = QBookReview.bookReview;

        return Optional.ofNullable(jpaQueryFactory.from(qBookReview)
                .select(new QBookReviewStatisticsDto(
                        qBookReview.count(),
                        qBookReview.rating.avg(),
                        qBookReview.rating.when(1).then(1).otherwise(0).sum(),
                        qBookReview.rating.when(2).then(1).otherwise(0).sum(),
                        qBookReview.rating.when(3).then(1).otherwise(0).sum(),
                        qBookReview.rating.when(4).then(1).otherwise(0).sum(),
                        qBookReview.rating.when(5).then(1).otherwise(0).sum(),
                        qBookReview.createdAt.max()
                ))
                .where(qBookReview.book.id.eq(bookId))
                .fetchOne());
    }
}
