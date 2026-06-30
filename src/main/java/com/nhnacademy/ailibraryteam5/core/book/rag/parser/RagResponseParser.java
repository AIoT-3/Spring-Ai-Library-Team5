package com.nhnacademy.ailibraryteam5.core.book.rag.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagResponseParser {

    private final ObjectMapper objectMapper;

    public List<BookAiRecommendationResponse> parse(String rawResponse, List<BookSearchResponse> candidates){
        List<Long> allowedBookIds = candidates.stream()
                .map(BookSearchResponse::getId)
                .toList();
        log.info("[RAG_PARSE] allowedBookIds count={}, sample={}", allowedBookIds.size(), sample(allowedBookIds.toString()));
        if(rawResponse == null || rawResponse.isBlank()) {
            log.warn("[RAG_PARSE] rawResponse is blank");
            return List.of();
        }
        log.info("[RAG_PARSE] rawResponse length={}, preview={}", rawResponse.length(), sample(rawResponse));
        String json = extraJson(rawResponse);
        log.info("[RAG_PARSE] extractedJson length={}, preview={}", json.length(), sample(json));
        try{
            List<BookAiRecommendationResponse> parsed = objectMapper.readValue(
                    json,new  TypeReference<List<BookAiRecommendationResponse>>(){}
            );
            List<BookAiRecommendationResponse> filtered = parsed.stream()
                    .filter(item -> allowedBookIds.contains(item.id())) //우리가 정해준 id로 나타내기 위해 사용 ai의 불안함을 방지
                    .toList();
            log.info("[RAG_PARSE] parsedCount={}, filteredCount={}, filtered={}",
                    parsed.size(), filtered.size(), filtered);
            return filtered;
        }catch(Exception e){
            log.warn("[RAG_PARSE] parse failed. extractedJson={}", sample(json), e);
            return List.of();
        }

    }
    private String extraJson(String raw){
        String cleaned = raw.replace("```json", "").replace("```", "").strip();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < start) {
            return "[]";
        }
        return cleaned.substring(start, end + 1);
    }

    private String sample(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }
}
