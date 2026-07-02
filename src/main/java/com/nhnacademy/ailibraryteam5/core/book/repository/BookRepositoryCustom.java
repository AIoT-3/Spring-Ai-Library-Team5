package com.nhnacademy.ailibraryteam5.core.book.repository;


import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface BookRepositoryCustom {
    Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request);

    Page<BookSearchResponse> vectorSearch(Pageable pageable,BookSearchRequest request);

    List<BookSearchResponse> findAllByIdList(List<Long> idList);
}
