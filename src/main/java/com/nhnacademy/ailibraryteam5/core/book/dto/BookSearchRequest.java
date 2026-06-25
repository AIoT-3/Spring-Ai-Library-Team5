package com.nhnacademy.ailibraryteam5.core.book.dto;


import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import jakarta.validation.constraints.Size;

public record BookSearchRequest(

        @Size(max = 100, message = "검색어는 100자 이하여야 합니다")
        String keyword,

        @Size(max = 20, message = "ISBN은 20자 이하여야 합니다")
        String isbn,

        SearchType searchType,

        float[] vector,

        Boolean isWarmUp
) {
    public BookSearchRequest{
        if(searchType == null){
            searchType = SearchType.KEYWORD;
        }
        if(isWarmUp == null){
            isWarmUp = false;
        }
    }

    public BookSearchRequest(String keyword, String isbn, SearchType searchType, float[] vector) {
        this(keyword, isbn, searchType, vector, null);
    }
}
