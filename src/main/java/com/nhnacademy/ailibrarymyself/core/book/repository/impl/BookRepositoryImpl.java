package com.nhnacademy.ailibrarymyself.core.book.repository.impl;

import com.nhnacademy.ailibrarymyself.core.book.domain.QBook;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibrarymyself.core.book.repository.BookRepositoryCustom;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookRepositoryImpl implements BookRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;
    private final QBook book = QBook.book;

    @Override
    public Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request) {
        BooleanBuilder where = commonWhere(request);

        List<BookSearchResponse> content = jpaQueryFactory
                .select(bookSearchProjection())
                .from(book)
                .where(where)
                .orderBy(book.id.asc()) //page 바꿀때 정렬이 풀리는걸 방지
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = jpaQueryFactory
                .select(book.count())
                .from(book)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private ConstructorExpression<BookSearchResponse> bookSearchProjection() {
        return Projections.constructor(
                BookSearchResponse.class,
                book.id,
                book.isbn,
                book.title,
                book.authorName,
                book.publisherName,
                book.price,
                book.editionPublishDate,
                book.imageUrl,
                book.bookContent,
                book.category
        );
    }

    private BooleanBuilder commonWhere(BookSearchRequest request) {
        BooleanBuilder builder = new BooleanBuilder();

        if (request == null) {
            return builder;
        }

        if (StringUtils.hasText(request.keyword())) {
            String keyword = request.keyword().trim();

            builder.and(
                    book.title.containsIgnoreCase(keyword)
                            .or(book.authorName.containsIgnoreCase(keyword))
                            .or(book.publisherName.containsIgnoreCase(keyword))
                            .or(book.subtitle.containsIgnoreCase(keyword))
                            .or(book.category.containsIgnoreCase(keyword))
                            .or(book.bookContent.containsIgnoreCase(keyword))
            );
        }

        if (StringUtils.hasText(request.isbn())) {
            builder.and(book.isbn.eq(request.isbn().trim()));
        }

        return builder;
    }
}
