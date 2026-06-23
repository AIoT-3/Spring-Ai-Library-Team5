package com.nhnacademy.ailibrarycustom.core.book.domain;

public enum SearchType {
    KEYWORD, // LIKE 검색
    VECTOR, // AI 임베딩 유사도
    HYBRID, // 키워드 + 유사도
    RAG // 자연어 검색 AI 답변 생성 검색
}
