package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagContextBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagPromptBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.cache.RagProperties;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookRagResult;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.core.book.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookRagService {

    private final BookSearchService bookSearchService;
    private final RagProperties ragProperties;
    private final RagContextBuilder ragContextBuilder;
    private final RagPromptBuilder ragPromptBuilder;
    private final BookAiService bookAiService;
    private final SematicRagCacheService sematicRagCacheService;
    private final EmbeddingService embeddingService;

    public BookRagResult RagSearch(Pageable pageable, BookSearchRequest request) {
        List<BookSearchResponse> candidates = List.of();
        long totalElements = 0;
        try{
            log.info("[RAG] search started. keyword={}, isbn={}, pageable={}",
                    request.keyword(), request.isbn(), pageable);
            Page<BookSearchResponse> candidatePage = bookSearchService.searchHybridCandidates(request, pageable);
            candidates = candidatePage.getContent();
            totalElements = candidatePage.getTotalElements();
            if(candidates.isEmpty()) {
                log.warn("[RAG] candidates are empty. fallback response will be returned.");
                return BookRagResult.fallback(candidates, totalElements);
            }
            if (pageable.getPageNumber() > 0) {
                return BookRagResult.fallback(candidates, totalElements);
            }

            float[] embedding = embeddingService.embed(request.keyword());

            BookSearchRequest searchRequest = new BookSearchRequest(
                    request.keyword(),
                    request.isbn(),
                    request.searchType(),
                    embedding
            );

            Optional<List<BookAiRecommendationResponse>> cached = sematicRagCacheService.findSimilar(searchRequest);
            if(cached.isPresent()) {
                return new BookRagResult(candidates, cached.get(), totalElements, true);
            }

            Page<BookSearchResponse> contextPage = bookSearchService.searchHybridCandidates(
                    request,
                    Pageable.ofSize(ragProperties.getCandidates())
            );
            String context = ragContextBuilder.build(contextPage.getContent());
            String prompt = ragPromptBuilder.build(request.keyword(),context);
            log.info("[RAG] contextLength={}, promptLength={}", context.length(), prompt.length());
            List<BookAiRecommendationResponse> recommend = bookAiService.call(prompt);

            log.info("[RAG] recommendation count={}, recommendations={}", recommend.size(), recommend);

            BookRagResult result = new BookRagResult(candidates, recommend, totalElements, true);
            sematicRagCacheService.save(request, embedding, recommend);
            return result;
        }catch(Exception e){
            log.warn("[RAG] failed. candidatesCount={}", candidates.size(), e);
            return BookRagResult.fallback(candidates, totalElements);
        }
    }
}
