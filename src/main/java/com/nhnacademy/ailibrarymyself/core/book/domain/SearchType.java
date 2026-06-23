package com.nhnacademy.ailibrarymyself.core.book.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SearchType {
    KEYWORD("keyword"),
    VECTOR("vector"),
    HYBRID("hybrid"),
    RAG("rag");

    private final String value;

    @JsonCreator
    public static SearchType from(String value) {
        return Arrays.stream(SearchType.values())
                .filter(type -> type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(KEYWORD);
    }
}