package com.nhnacademy.ailibrarycustom.core.book.dto;

import com.nhnacademy.ailibrarycustom.core.book.domain.SearchType;

import javax.validation.constraints.Size;

public record BookSearchRequest (
        @Size(max = 100, message = "검색어는 100자 이하여야 합니다")
        String keyword,

        @Size(max = 20, message = "ISBN은 20자 이하여야 합니다")
        String isbn,

        SearchType searchType,

        float[] vector,

        Boolean isWarmUp
){
    public String keyword(){
        return keyword != null ? keyword : "";
    }

    public String isbn(){
        return isbn != null ? isbn : "";
    }


}
