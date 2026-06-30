package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagContextBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagPromptBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookRagResult;
import com.nhnacademy.ailibraryteam5.core.book.rag.parser.RagResponseParser;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookRagService {

    private final BookSearchService bookSearchService;
    private final RagContextBuilder ragContextBuilder;
    private final RagPromptBuilder ragPromptBuilder;
    private final BookAiService bookAiService;
    private final RagResponseParser ragResponseParser;

    public BookRagResult RagSearch(Pageable pageable, BookSearchRequest request) {
        List<BookSearchResponse> candidates = List.of();
        try{
            log.info("[RAG] search started. keyword={}, isbn={}, pageable={}",
                    request.keyword(), request.isbn(), pageable);
            candidates = bookSearchService.searchHybridCandidates(request,100);
            log.info("[RAG] candidates count={}, sample={}", candidates.size(), sampleCandidates(candidates));
            if(candidates.isEmpty()) {
                log.warn("[RAG] candidates are empty. fallback response will be returned.");
                return BookRagResult.fallback(candidates);
            }
            String context = ragContextBuilder.build(candidates);
            String prompt = ragPromptBuilder.build(request.keyword(),context);
            log.info("[RAG] contextLength={}, promptLength={}", context.length(), prompt.length());
            List<BookAiRecommendationResponse> recommend = bookAiService.call(prompt);

            log.info("[RAG] recommendation count={}, recommendations={}", recommend.size(), recommend);

            return new BookRagResult(candidates,recommend,true);
        }catch(Exception e){
            log.warn("[RAG] failed. candidatesCount={}", candidates.size(), e);
            return BookRagResult.fallback(candidates);
        }
    }

    private String sampleCandidates(List<BookSearchResponse> candidates) {
        return candidates.stream()
                .limit(5)
                .map(book -> book.getId() + ":" + book.getTitle())
                .toList()
                .toString();
    }

}
