package com.nhnacademy.ailibrarycustom.core.review.repository.review.impl;

import com.nhnacademy.ailibrarycustom.core.review.domain.BookReview;
import com.nhnacademy.ailibrarycustom.core.review.domain.QBookReview;
import com.nhnacademy.ailibrarycustom.core.review.repository.review.BookReviewRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BookReviewRepositoryCustomImpl implements BookReviewRepositoryCustom {
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<BookReview> findNewReviewsAfterId(Long bookId, Long lastSummarizedCount) {
        QBookReview bookReview = QBookReview.bookReview;

        return jpaQueryFactory
                .selectFrom(bookReview)
                .where(
                        bookReview.book.id.eq(bookId)
                                .and(bookReview.id.gt(lastSummarizedCount)) // bookReview.id > 마지막으로 요약 처리를 완료한 리뷰의 ID 번호
                )
                .orderBy(bookReview.id.asc())
                .fetch();
    }
}
