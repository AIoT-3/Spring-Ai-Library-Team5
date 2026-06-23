package com.nhnacademy.ailibrarymyself.core.book.processor;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;

@Component
public class TextPreProcessor {
    public String clean(String text) {
        if(text == null || text.isBlank()){
            throw new IllegalArgumentException("Text cannot be null or blank");
        }
        StringEscapeUtils.unescapeHtml4(text);
        text = text.replaceAll("<[^>]*>"," ");
        text = text.trim().replaceAll("\\s+", " ");
        return text;
    }
}
