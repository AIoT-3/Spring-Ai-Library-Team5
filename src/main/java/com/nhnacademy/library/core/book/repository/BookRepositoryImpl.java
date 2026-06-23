package com.nhnacademy.library.core.book.repository;

import com.nhnacademy.library.core.book.domain.QBook;
import com.nhnacademy.library.core.book.dto.BookSearchRequest;
import com.nhnacademy.library.core.book.dto.BookSearchResponse;
import com.nhnacademy.library.core.book.dto.QBookSearchResponse;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookRepositoryImpl implements BookRepositoryCustom{
    private final JPAQueryFactory queryFactory;

    QBook book = QBook.book;

    @Override
    public Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request) {
        List<BookSearchResponse> results = queryFactory
                .select(new QBookSearchResponse(
                        book.id,
                        book.isbn,
                        book.volumeTitle,
                        book.title,
                        book.authorName,
                        book.publisherName,
                        book.firstPublishDate,
                        book.price,
                        book.bookContent,
                        book.imageUrl,
                        book.subtitle,
                        book.editionPublishDate
                ))
                .from(book)
                .where(commonWhere(request))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory
                .select(book.count())
                .from(book)
                .where(commonWhere(request))
                .fetchOne();

        return new PageImpl<>(results, pageable, total);
    }

    private BooleanBuilder commonWhere(BookSearchRequest request){
        BooleanBuilder builder = new BooleanBuilder();

        if(StringUtils.isNotEmpty(request.keyword())){
            String keyword = request.keyword();

            builder.or(book.title.containsIgnoreCase(keyword))
                    .or(book.authorName.containsIgnoreCase(keyword))
                    .or(book.publisherName.containsIgnoreCase(keyword));

            BooleanExpression fts = Expressions.booleanTemplate(
                    "function('ts_match_korean', {0}, {1}) = true",
                    book.bookContent,
                    keyword
            );
            builder.or(fts);
        }

        if(StringUtils.isNotEmpty(request.isbn())){
            builder.and(book.isbn.eq(request.isbn()));
        }

        return builder;
    }
}
