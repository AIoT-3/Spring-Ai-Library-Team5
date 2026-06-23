package com.nhnacademy.ailibrarycustom.core.book.repository.impl;

import com.nhnacademy.ailibrarycustom.core.book.domain.QBook;
import com.nhnacademy.ailibrarycustom.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarycustom.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibrarycustom.core.book.dto.QBookSearchResponse;
import com.nhnacademy.ailibrarycustom.core.book.repository.BookRepositoryCustom;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class BookRepositoryImpl implements BookRepositoryCustom {
    private final JPAQueryFactory jpaQueryFactory;

    QBook book = QBook.book;

    @Override
    public Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request) {
        List<BookSearchResponse> result = jpaQueryFactory
                .select(new QBookSearchResponse(
                book.id,
                book.isbn,
                book.title,
                book.authorName,
                book.publisherName,
                book.bookContent,
                book.imageUrl

        )).from(book)
                .where(searchCond(request)) // 필터 조건문 -> 카테고리만 선택 ? 가격대만 선택 ? 카테고리 + 가격대 + 브랜드 모두 선택 ?
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();// 리스트니까 다 물어와


        long total = jpaQueryFactory
                .select(book.count())
                .from(book)
                .where(searchCond(request))
                .fetchOne(); // 하나만 가져와

        return new PageImpl<>(result, pageable, total);
    }

    private BooleanBuilder searchCond(BookSearchRequest request){
        BooleanBuilder builder = new BooleanBuilder();

        if(request.keyword() != null && !request.keyword().isBlank()){
            builder.and(
                    book.title.containsIgnoreCase(request.keyword()).or(book.authorName.containsIgnoreCase(request.keyword()))
            );
        }
        if(request.isbn() != null && !request.isbn().isBlank()){
            builder.and(
                    book.isbn.eq(request.isbn())
            );
        }

        return builder;
    }
}
