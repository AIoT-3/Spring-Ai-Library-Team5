package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagContextBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.builder.RagPromptBuilder;
import com.nhnacademy.ailibraryteam5.core.book.rag.cache.RagProperties;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookRagResult;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.core.book.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookRagService {

    private final BookSearchService bookSearchService;
    private final BookRepository bookRepository;
    private final RagProperties ragProperties;
    private final RagContextBuilder ragContextBuilder;
    private final RagPromptBuilder ragPromptBuilder;
    private final BookAiService bookAiService;
    private final SematicRagCacheService sematicRagCacheService;
    private final EmbeddingService embeddingService;
    private final RecommendationScoreCalculator recommendationScoreCalculator;

    public BookRagResult RagSearch(Pageable pageable, BookSearchRequest request) {
        long totalStart = System.currentTimeMillis();
        List<BookSearchResponse> candidates = List.of();
        long totalElements = 0;
        try{
            log.info("[RAG] search started. keyword={}, isbn={}, pageable={}",
                    request.keyword(), request.isbn(), pageable);
            long candidateStart = System.currentTimeMillis();
            Page<BookSearchResponse> candidatePage = bookSearchService.searchHybridCandidates(request, pageable);
            log.info("[RAG_TIMING] candidateSearch={}ms, count={}, totalElements={}",
                    System.currentTimeMillis() - candidateStart,
                    candidatePage.getNumberOfElements(),
                    candidatePage.getTotalElements());
            candidates = candidatePage.getContent();
            totalElements = candidatePage.getTotalElements();
            if(candidates.isEmpty()) {
                log.warn("[RAG] candidates are empty. fallback response will be returned.");
                log.info("[RAG_TIMING] total={}ms, status=fallback_empty", System.currentTimeMillis() - totalStart);
                return BookRagResult.fallback(candidates, totalElements);
            }
            if (pageable.getPageNumber() > 0) {
                log.info("[RAG_TIMING] total={}ms, status=fallback_page", System.currentTimeMillis() - totalStart);
                return BookRagResult.fallback(candidates, totalElements);
            }

            long embeddingStart = System.currentTimeMillis();
            float[] embedding = embeddingService.embed(request.keyword());
            log.info("[RAG_TIMING] cacheEmbedding={}ms, dimension={}",
                    System.currentTimeMillis() - embeddingStart,
                    embedding.length);

            BookSearchRequest searchRequest = new BookSearchRequest(
                    request.keyword(),
                    request.isbn(),
                    request.searchType(),
                    embedding
            );

            long cacheStart = System.currentTimeMillis();
            Optional<List<BookAiRecommendationResponse>> cached = sematicRagCacheService.findSimilar(searchRequest);
            log.info("[RAG_TIMING] cacheLookup={}ms, hit={}",
                    System.currentTimeMillis() - cacheStart,
                    cached.isPresent());
            if(cached.isPresent()) {
                long hydrateStart = System.currentTimeMillis();
                List<BookAiRecommendationResponse> recommend = hydrateRecommendations(cached.get(), candidates, request);
                log.info("[RAG_TIMING] hydrate={}ms, total={}ms, status=cache_hit",
                        System.currentTimeMillis() - hydrateStart,
                        System.currentTimeMillis() - totalStart);
                return new BookRagResult(candidates, recommend, totalElements, true);
            }

            long contextSearchStart = System.currentTimeMillis();
            Page<BookSearchResponse> contextPage = bookSearchService.searchHybridCandidates(
                    request,
                    Pageable.ofSize(ragProperties.getCandidates())
            );
            log.info("[RAG_TIMING] contextSearch={}ms, count={}",
                    System.currentTimeMillis() - contextSearchStart,
                    contextPage.getNumberOfElements());
            long promptStart = System.currentTimeMillis();
            String context = ragContextBuilder.build(contextPage.getContent());
            String prompt = ragPromptBuilder.build(request.keyword(),context);
            log.info("[RAG_TIMING] promptBuild={}ms", System.currentTimeMillis() - promptStart);
            log.info("[RAG] contextLength={}, promptLength={}", context.length(), prompt.length());
            long aiStart = System.currentTimeMillis();
            List<BookAiRecommendationResponse> aiResponse = bookAiService.call(prompt, contextPage.getContent());
            log.info("[RAG_TIMING] aiCall={}ms, aiResponseCount={}",
                    System.currentTimeMillis() - aiStart,
                    aiResponse == null ? 0 : aiResponse.size());
            long hydrateStart = System.currentTimeMillis();
            List<BookAiRecommendationResponse> recommend = hydrateRecommendations(
                    aiResponse,
                    contextPage.getContent(),
                    request
            );
            log.info("[RAG_TIMING] hydrate={}ms", System.currentTimeMillis() - hydrateStart);

            log.info("[RAG] recommendation count={}, recommendations={}", recommend.size(), recommend);

            BookRagResult result = new BookRagResult(candidates, recommend, totalElements, true);
            long saveStart = System.currentTimeMillis();
            sematicRagCacheService.save(request, embedding, recommend);
            log.info("[RAG_TIMING] cacheSave={}ms, total={}ms, status=llm",
                    System.currentTimeMillis() - saveStart,
                    System.currentTimeMillis() - totalStart);
            return result;
        }catch(Exception e){
            log.warn("[RAG] failed. candidatesCount={}", candidates.size(), e);
            log.info("[RAG_TIMING] total={}ms, status=failed", System.currentTimeMillis() - totalStart);
            return BookRagResult.fallback(candidates, totalElements);
        }
    }

    private List<BookAiRecommendationResponse> hydrateRecommendations(
            List<BookAiRecommendationResponse> recommend,
            List<BookSearchResponse> candidates,
            BookSearchRequest request
    ) {
        if (recommend == null || recommend.isEmpty()) {
            return List.of();
        }

        Map<Long, BookSearchResponse> bookById = candidates.stream()
                .filter(book -> book.getId() != null)
                .collect(Collectors.toMap(
                        BookSearchResponse::getId,
                        book -> book,
                        (left, right) -> left
                ));

        List<Long> missingTitleBookIds = recommend.stream()
                .filter(item -> !hasText(item.title()))
                .map(BookAiRecommendationResponse::id)
                .filter(id -> !bookById.containsKey(id) || !hasText(bookById.get(id).getTitle()))
                .distinct()
                .toList();

        Map<Long, String> fetchedTitleById = new java.util.HashMap<>();

        if (!missingTitleBookIds.isEmpty()) {
            bookRepository.findAllById(missingTitleBookIds).stream()
                    .filter(book -> hasText(book.getTitle()))
                    .forEach(book -> fetchedTitleById.put(book.getId(), book.getTitle()));
        }

        double maxRrfScore = candidates.stream()
                .map(BookSearchResponse::getRrfScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        List<BookAiRecommendationResponse> hydrated = recommend.stream()
                .map(item -> {
                    BookSearchResponse book = bookById.get(item.id());
                    String title = hasText(item.title()) ? item.title() : resolveTitle(book, fetchedTitleById.get(item.id()));
                    Double similarity = book == null ? item.similarity() : book.getSimilarity();
                    Double rrfScore = book == null ? item.rrfScore() : book.getRrfScore();
                    Double recommendationScore = recommendationScoreCalculator.calculate(
                            similarity,
                            rrfScore,
                            maxRrfScore,
                            book,
                            request
                    );

                    return new BookAiRecommendationResponse(
                            item.id(),
                            title,
                            item.why(),
                            similarity,
                            rrfScore,
                            recommendationScore
                    );
                })
                .toList();
        return distinctRecommendationsByTitle(hydrated);
    }

    private List<BookAiRecommendationResponse> distinctRecommendationsByTitle(List<BookAiRecommendationResponse> recommend) {
        Map<String, BookAiRecommendationResponse> byTitle = new LinkedHashMap<>();
        for (BookAiRecommendationResponse item : recommend) {
            String key = titleKey(item.title());
            if (!hasText(key)) {
                key = "id:" + item.id();
            }
            byTitle.putIfAbsent(key, item);
        }
        return byTitle.values().stream().toList();
    }

    private String titleKey(String title) {
        if (!hasText(title)) {
            return "";
        }
        return title.replaceAll("\\s+", "").strip().toLowerCase();
    }

    private String resolveTitle(BookSearchResponse book, String fallbackTitle) {
        if (book != null && hasText(book.getTitle())) {
            return book.getTitle();
        }
        return fallbackTitle;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
