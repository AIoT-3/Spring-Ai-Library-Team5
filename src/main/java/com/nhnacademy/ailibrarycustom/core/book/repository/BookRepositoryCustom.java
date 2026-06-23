package com.nhnacademy.ailibrarycustom.core.book.repository;

import com.nhnacademy.ailibrarycustom.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarycustom.core.book.dto.BookSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookRepositoryCustom {
    Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request);
}
