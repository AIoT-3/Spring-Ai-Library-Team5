package com.nhnacademy.library.ai.function;

import com.nhnacademy.library.core.book.service.search.BookSearchService;
import com.nhnacademy.library.external.opennaru.client.LibraryInfoNaruApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Library Tools - @Tool 애노테이션 방식
 * <p>
 * Spring AI 1.1.2의 @Tool 애노테이션을 사용하여
 * 도서관 관련 기능을 LLM에 제공합니다.
 * </p>
 *
 * <p><b>장점:</b></p>
 * <ul>
 *   <li>애노테이션 기반으로 코드 간결</li>
 *   <li>@ToolParam으로 파라미터 설명 직접 제공</li>
 *   <li>Spring이 자동으로 스캔하여 ChatClient에 등록</li>
 * </ul>
 *
 * <p><b>사용법:</b></p>
 * <pre>
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultTools(libraryTools)  // 또는 .defaultToolNames("libraryTools")
 *     .build();
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryTools {

    private final BookSearchService bookSearchService;
    private final LibraryInfoNaruApiClient libraryInfoNaruApiClient;

    /**
     * 도서 검색 Tool
     *
     * @param title 검색할 도서 제목
     * @return 도서 검색 결과 목록
     */
    @Tool(
        description = """
            도서관 시스템에서 도서를 검색합니다.
            제목으로 검색할 수 있으며, 검색어를 받아 도서 목록과 ISBN을 반환합니다.

            **검색 대상:**
            - 도서관정보나루 API (전국 도서관 소장 도서)
            - 내부 DB (등록된 도서 정보)

            **반환 정보:**
            - 도서 제목
            - 저자명
            - 출판사
            - ISBN (13자리)
            """
    )
    public List<BookSearchResult> searchBooks(
        @ToolParam(
            description = "검색할 도서 제목 (예: 토비의 스프링, 자바의 정석)"
        ) String title) {

        log.info("[@Tool] searchBooks 호출: title={}", title);

        try {
            // 도서관정보나루 API 검색
            var libraryBooks = libraryInfoNaruApiClient.searchBooksByTitle(title);

            log.info("[@Tool] searchBooks 완료: {}권 검색됨", libraryBooks.size());

            return libraryBooks.stream()
                .map(book -> new BookSearchResult(
                    book.title(),
                    book.author(),
                    book.publisher(),
                    book.isbn13()
                ))
                .toList();

        } catch (Exception e) {
            log.error("[@Tool] searchBooks 실패: title={}", title, e);
            return List.of();
        }
    }

    /**
     * 도서관 검색 Tool
     *
     * @param region 지역 코드 (숫자)
     * @return 도서관 검색 결과 목록
     */
    @Tool(
        description = """
            지역별 도서관을 검색합니다.
            지역 코드(숫자)를 받아 해당 지역의 도서관 목록을 반환합니다.

            **⚠️ 매우 중요:**
            - 반드시 숫자 지역 코드를 사용하세요.
            - 한글 지역명(광주, 서울 등)을 사용하면 API 에러가 발생합니다.

            **지역 코드 예시:**
            - 서울=11, 부산=21, 대구=22, 인천=23
            - 광주=29, 대전=25, 울산=26, 세종=36
            - 경기=31, 강원=32, 충북=33, 충남=34
            - 전북=35, 전남=37, 경북=38, 경남=39

            **반환 정보:**
            - 도서관명
            - 도서관 코드
            - 주소
            - 전화번호
            """
    )
    public List<LibrarySearchResult> searchLibraries(
        @ToolParam(
            description = "지역 코드 (숫자, 예: 11=서울, 29=광주, 31=경기)"
        ) String region) {

        log.info("[@Tool] searchLibraries 호출: region={}", region);

        try {
            var libraries = libraryInfoNaruApiClient.searchLibrariesByRegion(region);

            log.info("[@Tool] searchLibraries 완료: {}개 도서관 검색됨", libraries.size());

            return libraries.stream()
                .map(lib -> new LibrarySearchResult(
                    lib.libName(),
                    lib.libCode(),
                    lib.address(),
                    lib.tel()
                ))
                .toList();

        } catch (Exception e) {
            log.error("[@Tool] searchLibraries 실패: region={}", region, e);
            return List.of();
        }
    }

    /**
     * 도서 소장 여부 확인 Tool
     *
     * @param libCode 도서관 코드
     * @param isbn13 ISBN 13자리
     * @return 도서 소장 정보
     */
    @Tool(
        description = """
            특정 도서관에 도서가 소장되어 있는지 확인합니다.
            도서관 코드(libCode)와 ISBN을 받아 소장 여부와 대출 가능 여부를 반환합니다.

            **파라미터:**
            - libCode: 도서관 코드 (예: "111001")
            - isbn13: ISBN 13자리 (예: "9788960777330")

            **반환 정보:**
            - 소장 여부 (hasBook)
            - 대출 가능 여부 (isAvailable)
            - 도서관명
            - 도서 정보
            """
    )
    public BookExistResult checkBookExists(
        @ToolParam(description = "도서관 코드 (예: 111001)")
        String libCode,

        @ToolParam(description = "ISBN 13자리 (예: 9788960777330)")
        String isbn13) {

        log.info("[@Tool] checkBookExists 호출: libCode={}, isbn={}", libCode, isbn13);

        try {
            var existInfo = libraryInfoNaruApiClient.checkBookExists(libCode, isbn13);

            log.info("[@Tool] checkBookExists 완료: hasBook={}, available={}",
                existInfo.hasBook(), existInfo.isAvailable());

            return new BookExistResult(
                existInfo.libName(),
                existInfo.hasBook(),
                existInfo.isAvailable(),
                existInfo.loanAvailable()
            );

        } catch (Exception e) {
            log.error("[@Tool] checkBookExists 실패: libCode={}, isbn={}", libCode, isbn13, e);
            return new BookExistResult(null, false, false, false);
        }
    }

    /**
     * 대출 가능한 도서관 검색 Tool
     *
     * @param isbn13 ISBN 13자리
     * @param region 지역 코드 (선택 사항)
     * @return 대출 가능한 도서관 목록
     */
    @Tool(
        description = """
            특정 도서에 대해 대출 가능한 도서관을 검색합니다.
            ISBN을 받아 해당 도서를 대출 가능한 도서관 목록을 반환합니다.

            **파라미터:**
            - isbn13: ISBN 13자리 (필수)
            - region: 지역 코드 (선택, 지정하면 해당 지역만 검색)

            **반환 정보:**
            - 도서관명
            - 도서관 코드
            - 대출 가능 여부
            - 대출 가능 수량
            """
    )
    public List<LoanAvailabilityResult> checkLoanAvailability(
        @ToolParam(description = "ISBN 13자리 (예: 9788960777330)")
        String isbn13,

        @ToolParam(
            description = "지역 코드 (선택, 예: 11=서울, 29=광주)",
            required = false  // 선택적 파라미터
        )
        String region) {

        log.info("[@Tool] checkLoanAvailability 호출: isbn={}, region={}", isbn13, region);

        try {
            if (region != null && !region.isBlank()) {
                // 지역별 대출 가능 도서관 검색
                var loanItems = libraryInfoNaruApiClient.searchLoanItemsByIsbn(isbn13, region);

                return loanItems.stream()
                    .filter(loan -> loan.loanAvailable().equals("Y"))
                    .map(loan -> new LoanAvailabilityResult(
                        loan.libName(),
                        loan.libCode(),
                        true,
                        loan.loanCount()
                    ))
                    .toList();
            } else {
                // 전체 대출 가능 도서관 검색
                var loanItems = libraryInfoNaruApiClient.searchLoanItemsByIsbn(isbn13);

                return loanItems.stream()
                    .filter(loan -> loan.loanAvailable().equals("Y"))
                    .map(loan -> new LoanAvailabilityResult(
                        loan.libName(),
                        loan.libCode(),
                        true,
                        loan.loanCount()
                    ))
                    .toList();
            }

        } catch (Exception e) {
            log.error("[@Tool] checkLoanAvailability 실패: isbn={}, region={}", isbn13, region, e);
            return List.of();
        }
    }

    // ===== Result DTOs =====

    /**
     * 도서 검색 결과
     */
    public record BookSearchResult(
        String title,
        String author,
        String publisher,
        String isbn
    ) {}

    /**
     * 도서관 검색 결과
     */
    public record LibrarySearchResult(
        String libraryName,
        String libraryCode,
        String address,
        String phoneNumber
    ) {}

    /**
     * 도서 소장 여부 결과
     */
    public record BookExistResult(
        String libraryName,
        boolean hasBook,
        boolean isAvailable,
        boolean loanAvailable
    ) {}

    /**
     * 대출 가능 여부 결과
     */
    public record LoanAvailabilityResult(
        String libraryName,
        String libraryCode,
        boolean isAvailable,
        int availableCount
    ) {}
}
