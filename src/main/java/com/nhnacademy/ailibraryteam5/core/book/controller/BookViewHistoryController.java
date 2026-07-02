package com.nhnacademy.ailibraryteam5.core.book.controller;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.core.history.domain.BookViewHistory;
import com.nhnacademy.ailibraryteam5.core.history.service.PersonalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/history")
@RequiredArgsConstructor
public class BookViewHistoryController {
    private final BookSearchService bookSearchService;
    private final PersonalizationService personalizationService;

    @GetMapping
    public String historyPage(
            @RequestParam(value = "userId", defaultValue = PersonalizationService.TEMP_USER_ID) String userId,
            @PageableDefault(size = 10, sort = "viewedAt", direction = Sort.Direction.DESC)Pageable pageable,
            Model model
            ){
        Page<BookViewHistory> historyPage = personalizationService.getRecentViewHistories(userId, pageable);
        List<BookSearchResponse> bookList = bookSearchService.searchByHistory(historyPage.getContent());

        model.addAttribute("books", bookList);
        model.addAttribute("historyPage", historyPage);
        model.addAttribute("request", new BookSearchRequest()); // fragments/pagination.html형식을 맞추기위한 가짜 리퀘스트

        return "index/history";
    }
}
