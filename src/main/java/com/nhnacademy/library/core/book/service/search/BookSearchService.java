package com.nhnacademy.library.core.book.service.search;

import com.nhnacademy.library.core.book.dto.BookSearchRequest;
import com.nhnacademy.library.core.book.dto.BookSearchResult;
import com.nhnacademy.library.core.book.repository.BookRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchService {
    private final BookRepositoryImpl bookRepository;

    @Transactional(readOnly = true)
    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request){
        log.info("BookSearchService - pageable: {}, request: {}", pageable, request);

        return BookSearchResult.builder()
                .books(bookRepository.search(pageable, request))
                .build();
    }
}
