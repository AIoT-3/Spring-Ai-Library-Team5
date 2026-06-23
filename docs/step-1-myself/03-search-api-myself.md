# 03. 현재 프로젝트 검색 화면/API 연결하기

이 문서는 강의자료 `docs/step-1/03.search-api.md`를 현재 프로젝트 기준으로 적용하는 순서다.

강의자료는 이미 검색용 Service, Result DTO, Controller가 있다고 보고 설명한다. 현재 프로젝트에서는 그 클래스가 없으면 먼저 만들어야 한다.

## 목표

Step 1의 목표는 복잡한 AI 검색이 아니라, CSV로 적재한 `Book` 데이터를 화면에서 검색할 수 있게 만드는 것이다.

```text
브라우저
  -> BookSearchController
      -> BookSearchService
          -> BookRepository.search(...)
              -> BookRepositoryImpl QueryDSL
                  -> BookSearchResponse DTO projection
```

여기까지 되면 이후 full-text search, vector search, RAG 검색을 붙일 기준점이 생긴다.

## 먼저 있어야 하는 클래스

아래 클래스가 없으면 03 단계의 Controller와 화면 코드는 바로 붙지 않는다.

| 클래스 | 위치 | 역할 |
| --- | --- | --- |
| `Book` | `core/book/domain` | DB 테이블과 매핑되는 Entity |
| `BookSearchRequest` | `core/book/dto` | 검색어, ISBN, 검색 타입을 받는 요청 DTO |
| `BookSearchResponse` | `core/book/dto` | 목록 화면에 필요한 필드만 담는 응답 DTO |
| `BookRepository` | `core/book/repository` | Spring Data JPA 기본 Repository |
| `BookRepositoryCustom` | `core/book/repository` | 커스텀 검색 메서드 선언 |
| `BookRepositoryImpl` | `core/book/repository/impl` | QueryDSL 검색 구현 |
| `QuerydslConfig` | `core/config` | `JPAQueryFactory` Bean 등록 |

현재 프로젝트에서는 `volumeTitle` 대신 `category`를 사용한다. DTO projection 순서도 생성자 순서와 맞아야 한다.

```java
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
        book.category
)
```

이 순서가 `BookSearchResponse` 생성자와 다르면 런타임에 projection 오류가 나거나 값이 잘못 들어간다.

## 1. 검색 결과 래퍼 만들기

Controller가 `Page<BookSearchResponse>`를 직접 알아도 되지만, 이후 검색 점수나 추천 문구 같은 값을 붙일 수 있도록 한 번 감싼다.

```java
package com.nhnacademy.ailibrarymyself.core.book.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

@Getter
@RequiredArgsConstructor
public class BookSearchResult {

    private final Page<BookSearchResponse> books;

    public static BookSearchResult of(Page<BookSearchResponse> books) {
        return new BookSearchResult(books);
    }
}
```

지금은 `books` 하나만 있어도 충분하다. 아직 구현하지 않은 `similarity`, `rrfScore`, `reviewSummary` 같은 값을 미리 넣지 않는 편이 덜 헷갈린다.

## 2. Service 만들기

Service는 Controller와 Repository 사이의 흐름을 담당한다.

```java
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

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(bookRepository.search(pageable, request));
    }
}
```

아직은 검색 전략 클래스를 따로 만들 필요가 없다. Step 1에서는 `KEYWORD` 검색만 안정적으로 동작시키고, Step 2 이후에 vector나 hybrid가 생길 때 전략 분리를 검토하면 된다.

## 3. Controller 만들기

화면용 Controller는 요청 파라미터를 받고, Service 결과를 템플릿에 전달한다.

```java
package com.nhnacademy.ailibrarymyself.core.book.controller;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibrarymyself.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibrarymyself.core.book.repository.BookRepository;
import com.nhnacademy.ailibrarymyself.core.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;
    private final BookRepository bookRepository;

    @GetMapping("/")
    public String index(
            @ModelAttribute BookSearchRequest request,
            @PageableDefault(size = 24) Pageable pageable,
            Model model
    ) {
        BookSearchResult result = bookSearchService.searchBooks(pageable, request);

        model.addAttribute("books", result.getBooks().getContent());
        model.addAttribute("page", result.getBooks());
        model.addAttribute("request", request);

        return "index/index";
    }

    @GetMapping("/books/{id}")
    public String detail(@PathVariable long id, Model model) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found. id=" + id));

        model.addAttribute("book", book);
        model.addAttribute("bookSummary", null);
        model.addAttribute("reviewSummary", null);

        return "index/book-detail";
    }
}
```

상세 화면에서 아직 요약이나 리뷰가 없으면 `null`로 넘기거나 템플릿에서 해당 블록을 제거한다.

## 4. 템플릿에서 현재 DTO 필드만 사용하기

`templates/index/index.html`에서는 현재 `BookSearchResponse`에 있는 값만 사용한다.

사용 가능한 대표 필드는 아래와 같다.

```text
id
isbn
title
authorName
publisherName
price
editionPublishDate
imageUrl
bookContent
category
```

`volumeTitle`은 현재 Entity에서 제거한 필드라 쓰면 안 된다. 카테고리는 `book.category`로 출력한다.

## 5. 테스트는 test 프로필로 단순화하기

Repository 테스트에서 H2 설정을 매번 annotation에 길게 쓰면 코드가 복잡해진다. 테스트 전용 설정은 `src/test/resources/application-test.properties`에 둔다.

테스트 클래스는 아래 정도만 남긴다.

```java
@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class BookRepositoryImplTest {
}
```

`application-test.properties`에 H2 설정을 모아두면 “이 테스트는 H2로 돌린다”는 의도가 더 잘 보인다.

## 현재 단계에서 하지 않는 것

아래는 나중 단계로 미루는 편이 좋다.

| 미루는 것 | 이유 |
| --- | --- |
| 검색 전략 클래스 여러 개 | 아직 `KEYWORD` 하나라 구조만 복잡해짐 |
| vector 검색 | embedding 생성과 pgvector 설정이 먼저 필요함 |
| RAG 검색 | 기본 검색 결과가 안정화된 뒤 붙여야 문제를 구분하기 쉬움 |
| PostgreSQL 전문 검색 | H2 단위 테스트와 분리해서 검증해야 함 |

Step 1에서는 화면, 페이징, DTO projection, 기본 키워드 검색이 안정적으로 동작하는지 확인하는 것이 먼저다.
