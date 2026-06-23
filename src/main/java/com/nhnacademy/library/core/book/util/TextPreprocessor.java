package com.nhnacademy.library.core.book.util;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

public class TextPreprocessor {
    private static final String HTML_TAG_PATTERN = "<[^>]*>";
    private static final String SPECIAL_CHAR_PATTERN = "[^가-힣a-zA-Z0-9\\s]";
    private static final String SPACE_PATTERN = "\\s+";

    public static String preprocess(String text) {
        // 1. null 체크
        if (!StringUtils.hasText(text)) {
            return "";
        }

        // 2. HTML 엔티티 디코딩
        String decoded = StringEscapeUtils.unescapeHtml4(text);

        // 3. HTML 태그 제거
        String cleaned = decoded.replaceAll(HTML_TAG_PATTERN, " ");

        // 4. 특수문자 제거
        cleaned = cleaned.replaceAll(SPECIAL_CHAR_PATTERN, "");

        // 5. 연속된 공백 통합
        cleaned = cleaned.replaceAll(SPACE_PATTERN, " ");

        // 6. 앞뒤 공백 제거 및 소문자 변환
        return cleaned.trim().toLowerCase();
    }
}
