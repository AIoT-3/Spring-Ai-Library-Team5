package com.nhnacademy.ailibraryteam5.core.book.processor;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

@Component
public class TextPreProcessor {
    private static final String HTML_TAG_PATTERN = "<[^>]*>";
    private static final String SPECIAL_CHAR_PATTERN = "[^가-힣a-zA-Z0-9\\s]";
    private static final String SPACE_PATTERN = "\\s+";

    public static String clean(String text) {
        if(text == null || text.isBlank()){
            throw new IllegalArgumentException("Text cannot be null or blank");
        }
        text = StringEscapeUtils.unescapeHtml4(text);
        text = text.replaceAll(HTML_TAG_PATTERN," ");
        text = text.trim().replaceAll(SPACE_PATTERN, " ");
        text = text.replaceAll(SPECIAL_CHAR_PATTERN," ");
        return text.trim();
    }
}
