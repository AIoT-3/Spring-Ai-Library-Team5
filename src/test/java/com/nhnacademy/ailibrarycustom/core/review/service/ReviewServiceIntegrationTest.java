package com.nhnacademy.ailibrarycustom.core.review.service;

import com.nhnacademy.ailibrarycustom.core.book.domain.Book;
import com.nhnacademy.ailibrarycustom.core.book.repository.BookRepository;
import com.nhnacademy.ailibrarycustom.core.review.domain.BookReviewSummary;
import com.nhnacademy.ailibrarycustom.core.review.dto.ReviewCreateRequest;
import com.nhnacademy.ailibrarycustom.core.review.repository.summary.BookReviewSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
class ReviewServiceIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookReviewSummaryRepository bookReviewSummaryRepository;

    @Test
    void testEntireReviewSummaryPipeline() throws InterruptedException {
        // 1. 테스트용 대상 도서 지정 (DB에 실제로 존재하는 1번 책 혹은 임의의 첫 번째 책을 가져옵니다)
        Book book = bookRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("테스트를 실행할 도서 데이터가 DB에 존재하지 않습니다."));
        Long bookId = book.getId();
        log.info("▶ 테스트 대상 도서 선정 완료: bookId={}, title={}", bookId, book.getTitle());

        // 2. AI 요약 작동 조건인 "리뷰 5개"를 채우기 위해 5번 리뷰를 등록합니다.
        log.info("▶ 5개의 리뷰 등록 시작 (포그라운드 동작)");
        reviewService.createReview(bookId, new ReviewCreateRequest("이 책 진짜 명작이네요. 강력 추천합니다!", 5));
        reviewService.createReview(bookId, new ReviewCreateRequest("초보자가 읽기에는 다소 난이도가 있네요.", 3));
        reviewService.createReview(bookId, new ReviewCreateRequest("예제 코드가 풍부해서 이해하기 쉽습니다.", 5));
        reviewService.createReview(bookId, new ReviewCreateRequest("번역이 다소 매끄럽지 못한 부분이 아쉽습니다.", 3));
        reviewService.createReview(bookId, new ReviewCreateRequest("소장 가치가 충분한 책입니다. 꼭 읽어보세요.", 5));

        // 3. 비동기 백그라운드 작업(RabbitMQ 전송 -> 수신 -> AI 요약문 집필)이
        // 완료될 때까지 백그라운드 스레드가 동작할 시간을 잠시 기다려 줍니다. (5초 대기)
        log.info("▶ 비동기 AI 요약 집필 중... 5초간 대기합니다.");
        Thread.sleep(5000);

        // 4. 최종 결과 확인 (DB의 BookReviewSummary 테이블 조회)
        Optional<BookReviewSummary> summaryOptional = bookReviewSummaryRepository.findById(bookId);

        assertThat(summaryOptional).isPresent();
        BookReviewSummary summary = summaryOptional.get();

        log.info("==================================================");
        log.info("🎉 [최종 통계 및 AI 요약본 결과 출력] 🎉");
        log.info("총 리뷰 개수: {}개", summary.getReviewCount());
        log.info("평균 평점: {}점", summary.getAverageRating());
        log.info("AI 한 줄 요약: {}", summary.getReviewSummary());
        log.info("is_generating 푯말 상태: {}", summary.getIsGenerating());
        log.info("is_dirty 포스트잇 상태: {}", summary.getIsSummaryDirty());
        log.info("==================================================");

        // 검증 (통계 수치가 5개 이상 채워졌고, 요약본 글자가 null이 아니고 정상 집필되었는지 확인)
        assertThat(summary.getReviewCount()).isEqualTo(5);
        assertThat(summary.getReviewSummary()).isNotBlank();
    }
}
