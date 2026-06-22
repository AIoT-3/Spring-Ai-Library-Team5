package com.nhnacademy.library.front.web;

import com.nhnacademy.library.core.book.dto.BookSearchRequest;
import com.nhnacademy.library.core.book.dto.BookSearchResult;
import com.nhnacademy.library.core.book.service.search.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class BookSearchController {
    private final BookSearchService bookSearchService;

    @GetMapping("/")
    public String index(
            @ModelAttribute BookSearchRequest request,
            @PageableDefault(size = 24)Pageable pageable,
            Model model
            ){
        BookSearchResult result = bookSearchService.searchBooks(pageable, request);

        model.addAttribute("books", result.getBooks().getContent());
        model.addAttribute("page", result.getBooks());
        model.addAttribute("request", request);

        return "index/index";
    }
}
