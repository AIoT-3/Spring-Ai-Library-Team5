package com.nhnacademy.ailibrarymyself.core.book.repository.impl;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import com.nhnacademy.ailibrarymyself.core.book.domain.SearchType;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibrarymyself.core.book.repository.BookRepository;
import com.nhnacademy.ailibrarymyself.core.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class BookRepositoryImplTest {

    @Autowired
    BookRepository bookRepository;

    @Test
    @DisplayName("키워드로 제목, 저자, 출판사, 부제, 카테고리, 본문을 검색한다")
    void searchByKeyword() {
        bookRepository.save(book(
                "9781111111111",
                "자바의 정석",
                "남궁성",
                "도우출판",
                "자바 입문서",
                "000",
                "기초편"
        ));

        BookSearchRequest request = new BookSearchRequest(
                "자바",
                null,
                SearchType.KEYWORD,
                null
        );

        Page<BookSearchResponse> result = bookRepository.search(PageRequest.of(0, 10), request);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).contains("자바");
        assertThat(result.getContent().get(0).getPublisherName()).isEqualTo("도우출판");
        assertThat(result.getContent().get(0).getBookContent()).isEqualTo("자바 입문서");
    }

    @Test
    @DisplayName("검색 조건이 없으면 id 오름차순으로 전체 목록을 페이지 조회한다")
    void searchWithoutCondition() {
        bookRepository.save(book(
                "9781111111111",
                "자바의 정석",
                "남궁성",
                "도우출판",
                "자바 입문서",
                "000",
                "기초편"
        ));
        bookRepository.save(book(
                "9782222222222",
                "스프링 입문",
                "김스프링",
                "길벗",
                "스프링 기초",
                "100",
                "입문편"
        ));
        bookRepository.save(book(
                "9783333333333",
                "데이터베이스 개론",
                "박데이터",
                "한빛",
                "SQL 기초",
                "200",
                "개론"
        ));

        Page<BookSearchResponse> result = bookRepository.search(
                PageRequest.of(0, 2),
                new BookSearchRequest(null, null, SearchType.KEYWORD, null)
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(BookSearchResponse::getIsbn)
                .containsExactly("9781111111111", "9782222222222");
    }

    @Test
    @DisplayName("ISBN은 정확히 일치하는 도서만 검색한다")
    void searchByIsbn() {
        bookRepository.save(book(
                "9781111111111",
                "자바의 정석",
                "남궁성",
                "도우출판",
                "자바 입문서",
                "000",
                "기초편"
        ));
        bookRepository.save(book(
                "9782222222222",
                "자바 실전",
                "김자바",
                "길벗",
                "자바 실전서",
                "100",
                "실전편"
        ));

        Page<BookSearchResponse> result = bookRepository.search(
                PageRequest.of(0, 10),
                new BookSearchRequest(null, "9782222222222", SearchType.KEYWORD, null)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("자바 실전");
    }

    @Test
    @DisplayName("키워드와 ISBN 조건은 AND로 함께 적용한다")
    void searchByKeywordAndIsbn() {
        bookRepository.save(book(
                "9781111111111",
                "자바의 정석",
                "남궁성",
                "도우출판",
                "자바 입문서",
                "000",
                "기초편"
        ));
        bookRepository.save(book(
                "9782222222222",
                "스프링 입문",
                "김스프링",
                "길벗",
                "스프링 기초",
                "100",
                "입문편"
        ));

        Page<BookSearchResponse> result = bookRepository.search(
                PageRequest.of(0, 10),
                new BookSearchRequest("자바", "9782222222222", SearchType.KEYWORD, null)
        );

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("본문(bookContent)도 Step 1 키워드 검색 대상에 포함한다")
    void searchByBookContent() {
        bookRepository.save(book(
                "9781111111111",
                "프로그래밍 입문",
                "남궁성",
                "도우출판",
                "객체지향과 JVM을 설명하는 자바 입문서",
                "000",
                "기초편"
        ));

        Page<BookSearchResponse> result = bookRepository.search(
                PageRequest.of(0, 10),
                new BookSearchRequest("JVM", null, SearchType.KEYWORD, null)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getBookContent()).contains("JVM");
    }

    private static Book book(
            String isbn,
            String title,
            String authorName,
            String publisherName,
            String bookContent,
            String category,
            String subtitle
    ) {
        return new Book(
                isbn,
                title,
                authorName,
                publisherName,
                LocalDate.of(2021, 1, 1),
                BigDecimal.valueOf(12000),
                "https://example.com/book.jpg",
                bookContent,
                category,
                subtitle,
                LocalDate.of(2021, 2, 1)
        );
    }
}
