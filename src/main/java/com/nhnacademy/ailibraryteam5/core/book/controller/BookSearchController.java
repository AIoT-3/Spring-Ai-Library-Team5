package com.nhnacademy.ailibraryteam5.core.book.controller;

import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookRagResult;
import com.nhnacademy.ailibraryteam5.core.book.rag.service.BookRagService;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;
    private final BookRagService bookRagService;
    private final BookRepository bookRepository;

    @GetMapping("/")
    public String index(
            @ModelAttribute BookSearchRequest request,
            @PageableDefault(size = 24) Pageable pageable,
            Model model
    ) {
        if(request.searchType() == SearchType.RAG){
            BookRagResult result = bookRagService.RagSearch(pageable,request);
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
    public String detail(@PathVariable long id, Model model) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found. id=" + id));

        model.addAttribute("book", book);
        model.addAttribute("bookSummary", null);
        model.addAttribute("reviewSummary", null);

        return "index/book-detail";
    }
}
