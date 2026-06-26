package com.nhnacademy.ailibraryteam5.core.book.rag.builder;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class RagContextBuilder {
    private static final int MAX_CONTENT_LENGTH = 300;

    public String build(List<BookSearchResponse> books) {
        if(books == null || books.isEmpty()) {
            log.warn("[RAG_CONTEXT] books are empty");
            return "";
        }
        log.info("[RAG_CONTEXT] building context. bookCount={}, maxContentLength={}", books.size(), MAX_CONTENT_LENGTH);
        StringBuilder sb = new StringBuilder("## 참고도서\n\n");

        for(int i = 0; i < books.size(); i++) {
            BookSearchResponse book = books.get(i);
            sb.append("### 도서 ").append(i+1).append('\n');
            sb.append("_ ID: ").append(book.getId()).append('\n');
            sb.append("_ 제목: ").append(nullToDash(book.getTitle())).append('\n');
            sb.append("_ 저자: ").append(nullToDash(book.getAuthorName())).append('\n');
            sb.append("_ 출판사: ").append(nullToDash(book.getPublisherName())).append('\n');
            sb.append("_ 카테고리: ").append(nullToDash(book.getCategory())).append('\n');
            sb.append("_ 소개: ").append(truncate(book.getBookContent(),MAX_CONTENT_LENGTH)).append('\n');
        }
        log.info("[RAG_CONTEXT] context built. length={}", sb.length());
        return sb.toString();
    }
    //strip() 문자열 공백 제거 trim()과의 차이점 유니코드 기준 공백 처리
    private String nullToDash(String value){
        return value == null || value.isBlank() ? "" : value.strip();
    }
    private String truncate(String value, int maxLength) {
        if(value == null || value.isBlank()) {
            return "-";
        }
        String normal = value.replaceAll("\\s+"," ").strip();
        return normal.length() <= maxLength ? normal : normal.substring(0, maxLength);
    }
}
