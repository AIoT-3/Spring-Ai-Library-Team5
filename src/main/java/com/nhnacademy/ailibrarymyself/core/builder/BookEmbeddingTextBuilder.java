package com.nhnacademy.ailibrarymyself.core.builder;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import org.springframework.stereotype.Component;

@Component
public class BookEmbeddingTextBuilder {

    public String build(Book book) {
        StringBuilder sb = new StringBuilder();

        append(sb, "ISBN", book.getIsbn(), 50);
        append(sb, "제목", book.getTitle(), 200);
        append(sb, "저자", book.getAuthorName(), 100);
        append(sb, "출판사", book.getPublisherName(), 100);
        append(sb, "최초 출간일", book.getFirstPublishDate(), 30);
        append(sb, "가격", book.getPrice(), 30);
        append(sb, "이미지 URL", book.getImageUrl(), 500);
        append(sb, "도서 내용", book.getBookContent(), 2000);
        append(sb, "카테고리", book.getCategory(), 100);
        append(sb, "부제", book.getSubtitle(), 300);
        append(sb, "판본 출간일", book.getEditionPublishDate(), 30);

        return sb.toString();
    }

    private void append(StringBuilder sb, String label, Object value, int maxLength) {
        String text = safe(value);

        if (text == null) {
            return;
        }

        text = truncate(text, maxLength);

        sb.append(label)
                .append(": ")
                .append(text)
                .append("\n");
    }

    private String safe(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString();

        // 앞뒤 공백 제거 + 연속 공백을 하나로 정리
        text = text.trim().replaceAll("\\s+", " ");

        if (text.isBlank()) {
            return null;
        }

        return text;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }

        return text.substring(0, maxLength - 3) + "...";
    }
}
