package com.nhnacademy.ailibraryteam5.core.book.controller;

import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookRagResult;
import com.nhnacademy.ailibraryteam5.core.book.rag.service.BookRagService;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.core.history.service.PersonalizationService;
import com.nhnacademy.ailibraryteam5.core.review.domain.BookReviewSummary;
import com.nhnacademy.ailibraryteam5.core.review.dto.ReviewCreateRequest;
import com.nhnacademy.ailibraryteam5.core.review.dto.ReviewResponse;
import com.nhnacademy.ailibraryteam5.core.review.repository.summary.BookReviewSummaryRepository;
import com.nhnacademy.ailibraryteam5.core.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;
    private final BookRagService bookRagService;
    private final BookRepository bookRepository;
    private final PersonalizationService personalizationService;
    private final ReviewService reviewService;
    private final BookReviewSummaryRepository bookReviewSummaryRepository;

    @GetMapping("/")
    public String index(
            @ModelAttribute BookSearchRequest request,
            @PageableDefault(size = 24) Pageable pageable,
            Model model
    ) {
        if(request.searchType() == SearchType.RAG){
            BookRagResult result = bookRagService.ragSearch(pageable,request);
            model.addAttribute("books", result.books());
            model.addAttribute("page", new PageImpl<>(result.books(), pageable, result.totalElements()));
            model.addAttribute("aiAvailable", result.aiAvailable());
            model.addAttribute("recommend", result.recommend());
            model.addAttribute("request", request);
        }else{
            BookSearchResult result = bookSearchService.searchBooks(pageable, request);
            model.addAttribute("books", result.getBooks().getContent());
            model.addAttribute("page", result.getBooks());
            model.addAttribute("request", request);
        }

        return "index/index";
    }

    @GetMapping("/books/{id}")
    public String detail(
            @PathVariable long id,
            @PageableDefault(size = 5) Pageable pageable,
            Model model
    ) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found. id=" + id));

        personalizationService.userSavedBook(PersonalizationService.TEMP_USER_ID, id);

        // 해당 도서의 실제 리뷰 목록 페이징 조회
        Page<ReviewResponse> reviewPage = reviewService.getReviews(id, pageable);
        // 해당 도서의 통계 및 요약본 조회
        BookReviewSummary bookSummary = bookReviewSummaryRepository.findById(id).orElse(null);

        model.addAttribute("book", book);
        model.addAttribute("reviewPage", reviewPage);
        model.addAttribute("reviews", reviewPage.getContent());
        model.addAttribute("bookSummary", bookSummary);
        model.addAttribute("reviewSummary", bookSummary != null ? bookSummary.getReviewSummary() : null);

        return "index/book-detail";
    }

    @PostMapping("/books/{bookId}/reviews")
    public String createReview(
            @PathVariable Long bookId,
            @ModelAttribute ReviewCreateRequest request,
            RedirectAttributes redirectAttributes
    ) {
        reviewService.createReview(bookId, request);
        redirectAttributes.addFlashAttribute("successMessage", "리뷰가 성공적으로 등록되었습니다!");
        return "redirect:/books/" + bookId;
    }
}

