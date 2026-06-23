package com.nhnacademy.ailibrarymyself.core.book.repository;

import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BookRepositoryCustom {
    Page<BookSearchResponse> search(Pageable pageable,BookSearchRequest request);
}
