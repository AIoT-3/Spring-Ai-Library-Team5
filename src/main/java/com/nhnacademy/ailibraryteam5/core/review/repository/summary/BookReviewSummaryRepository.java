package com.nhnacademy.ailibraryteam5.core.review.repository.summary;

import com.nhnacademy.ailibraryteam5.core.review.domain.BookReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookReviewSummaryRepository extends JpaRepository<BookReviewSummary, Long>, BookReviewSummaryRepositoryCustom {

}
