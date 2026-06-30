package com.nhnacademy.ailibrarycustom.core.review.repository.summary;

import com.nhnacademy.ailibrarycustom.core.review.domain.BookReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookReviewSummaryRepository extends JpaRepository<BookReviewSummary, Long>, BookReviewSummaryRepositoryCustom {

}
