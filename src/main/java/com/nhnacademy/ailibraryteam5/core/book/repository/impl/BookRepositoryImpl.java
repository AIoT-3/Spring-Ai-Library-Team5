package com.nhnacademy.ailibraryteam5.core.book.repository.impl;

import com.nhnacademy.ailibraryteam5.core.book.domain.QBook;
import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepositoryCustom;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import java.util.List;
@Slf4j
@Component
@RequiredArgsConstructor
public class BookRepositoryImpl implements BookRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final Environment environment;
    private final QBook book = QBook.book;

    @Override
    public Page<BookSearchResponse> search(Pageable pageable, BookSearchRequest request) {

        if (request.searchType() == SearchType.VECTOR && request.vector() != null) {
            return vectorSearch(pageable, request);
        }

        List<String> keywordTokens = keywordTokens(request.keyword());

        // 1. Book 조회 (BookSearchResponse.from() 사용)
        List<BookSearchResponse> bookSearchResponseList = queryFactory
                .from(book)
                .select(
                        Projections.constructor(
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
                                book.category,
                                Expressions.nullExpression(Double.class),
                                Expressions.nullExpression(Double.class),
                                book.embedding
                        )
                )
                .where(commonWhere(request))
                .orderBy(orderByKeywordScore(keywordTokens))
                .orderBy(book.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long totalCount = queryFactory
                .select(book.count())
                .from(book)
                .where(commonWhere(request))
                .fetchOne();

        return new PageImpl<>(bookSearchResponseList, pageable, totalCount);
    }

    @Override
    public Page<BookSearchResponse> vectorSearch(Pageable pageable, BookSearchRequest request) {
        if (request.vector() == null) {
            log.warn("[VECTOR_SEARCH] Vector is null, returning empty result");
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String vectorString = arrayToVectorString(request.vector());

        NumberTemplate<Double> similarityTemplate = Expressions.numberTemplate(
                Double.class,
                "function('vector_cosine_similarity', {0}, {1})",
                book.embedding, vectorString);

        // 1. Book 벡터 검색
        List<BookSearchResponse> bookSearchResponseList = queryFactory
                .from(book)
                .select(
                        Projections.constructor(
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
                                book.category,
                                similarityTemplate,
                                Expressions.nullExpression(Double.class),
                                book.embedding
                        )
                )
                .where(book.embedding.isNotNull())
                .orderBy(similarityTemplate.desc())
                .orderBy(book.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long totalCount = queryFactory
                .select(book.count())
                .from(book)
                .where(book.embedding.isNotNull())
                .fetchOne();

        return new PageImpl<>(bookSearchResponseList, pageable, totalCount);
    }

    private String arrayToVectorString(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("Vector cannot be null");
        }

        // Arrays.toString()을 사용하여 불필요한 루프 제거
        // Arrays.toString()은 [1.0, 2.0, 3.0] 형식으로 반환
        return Arrays.toString(vector);
    }

    private OrderSpecifier<?>[] orderByKeywordScore(List<String> tokens) {
        if (tokens.isEmpty()) {
            return new OrderSpecifier<?>[]{book.id.asc()};
        }

        return new OrderSpecifier<?>[]{keywordScore(tokens).desc(), book.id.asc()};
    }

    private NumberExpression<Integer> keywordScore(List<String> tokens) {
        NumberExpression<Integer> score = null;
        for (String token : tokens) {
            score = addScore(score, matchScore(book.title.containsIgnoreCase(token), 100));
            score = addScore(score, matchScore(book.authorName.containsIgnoreCase(token), 100));
            score = addScore(score, matchScore(book.subtitle.containsIgnoreCase(token), 80));
            score = addScore(score, matchScore(book.category.containsIgnoreCase(token), 70));
            score = addScore(score, matchScore(book.bookContent.containsIgnoreCase(token), 60));
            score = addScore(score, matchScore(book.publisherName.containsIgnoreCase(token), 40));
        }
        return score;
    }

    private NumberExpression<Integer> addScore(NumberExpression<Integer> score, NumberExpression<Integer> addend) {
        return score == null ? addend : score.add(addend);
    }

    private NumberExpression<Integer> matchScore(BooleanExpression condition, int score) {
        return new CaseBuilder()
                .when(condition).then(score)
                .otherwise(0);
    }

    private List<String> keywordTokens(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return List.of();
        }
        return Arrays.stream(keyword.strip().split("\\s+"))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private BooleanBuilder commonWhere(BookSearchRequest request) {
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.isNotEmpty(request.keyword())) {
            String keyword = request.keyword();
            List<String> tokens = keywordTokens(keyword);
            log.info("[BOOK_REPOSITORY] Applying keyword filter: {}", keyword);

            BooleanBuilder keywordBuilder = new BooleanBuilder();
            // 1. LIKE 검색
            keywordBuilder.or(book.title.containsIgnoreCase(keyword))
                    .or(book.authorName.containsIgnoreCase(keyword))
                    .or(book.publisherName.containsIgnoreCase(keyword))
                    .or(book.subtitle.containsIgnoreCase(keyword))
                    .or(book.category.containsIgnoreCase(keyword))
                    .or(book.bookContent.containsIgnoreCase(keyword));

            for (String token : tokens) {
                keywordBuilder.or(book.title.containsIgnoreCase(token))
                        .or(book.authorName.containsIgnoreCase(token))
                        .or(book.publisherName.containsIgnoreCase(token))
                        .or(book.subtitle.containsIgnoreCase(token))
                        .or(book.category.containsIgnoreCase(token))
                        .or(book.bookContent.containsIgnoreCase(token));
            }

            // 2. Full Text Search (PostgreSQL 전용)
            if (isPostgresProfile()) {
                BooleanExpression fts = Expressions.booleanTemplate(
                        "function('ts_match_korean', {0}, {1}) = true",
                        book.bookContent,
                        keyword
                );
                keywordBuilder.or(fts);
            }

            builder.and(keywordBuilder);
        }

        if (StringUtils.isNotEmpty(request.isbn())) {
            log.info("[BOOK_REPOSITORY] Applying isbn filter: {}", request.isbn());
            builder.and(book.isbn.eq(request.isbn()));
        }

        return builder;
    }

    private boolean isPostgresProfile() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return false;
        }

        String driverClassName = environment.getProperty("spring.datasource.driver-class-name", "");
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        String dialect = environment.getProperty("spring.jpa.properties.hibernate.dialect", "");

        if (driverClassName.toLowerCase().contains("h2")
                || datasourceUrl.toLowerCase().contains("h2")
                || dialect.toLowerCase().contains("h2")) {
            return false;
        }

        return driverClassName.toLowerCase().contains("postgresql")
                || datasourceUrl.toLowerCase().contains("postgresql")
                || dialect.toLowerCase().contains("postgresql");
    }

}
