package com.nhnacademy.ailibrarymyself.core.book.service;

import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibrarymyself.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookSearchService {

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return switch (request.searchType()){
            case KEYWORD -> keywordSearch(pageable,request);
            case VECTOR -> keywordSearch(pageable,request); // 만든뒤 바꿔줘야함
            case HYBRID -> keywordSearch(pageable, request);
            case RAG    -> keywordSearch(pageable, request);
        };

    }
    private BookSearchResult keywordSearch(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(bookRepository.search(pageable,request));
    }
}
