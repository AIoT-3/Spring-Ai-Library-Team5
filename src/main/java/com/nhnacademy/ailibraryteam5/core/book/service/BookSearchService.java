package com.nhnacademy.ailibraryteam5.core.book.service;

import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.history.service.PersonalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BookSearchService {
    private static final int RRF_K = 60;
    private static final int MIN_HYBRID_CANDIDATES = 100;

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final PersonalizationService personalizationService;

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return switch (request.searchType()){
            case KEYWORD -> keywordSearch(pageable,request);
            case VECTOR -> vectorSearch(pageable,request);
            case HYBRID -> hybridSearch(pageable, request); // 만든뒤 바꿔줘야함
            case RAG    -> keywordSearch(pageable, request);
            case PERSONALIZED -> personalizedSearch(pageable, request);
        };

    }
    private BookSearchResult keywordSearch(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(bookRepository.search(pageable,request));
    }
    private BookSearchResult vectorSearch(Pageable pageable, BookSearchRequest request) {
        float[] vector = embeddingService.embed(request.keyword());
        BookSearchRequest vectorRequest = new BookSearchRequest(
                request.keyword(),
                request.isbn(),
                SearchType.VECTOR,
                vector
        );
        return BookSearchResult.of(bookRepository.search(pageable,vectorRequest));
    }
    private BookSearchResult hybridSearch(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(searchHybridCandidates(request, pageable));
    }

    private BookSearchResult personalizedSearch(Pageable pageable, BookSearchRequest request){
        log.info("개인화 도서 검색");
        Page<BookSearchResponse> page = searchHybridCandidates(request, pageable);

        List<BookSearchResponse> result = page.getContent();
        List<BookSearchResponse> personalizedResult = personalizationService.personalizeSort(result, PersonalizationService.TEMP_USER_ID);

        return BookSearchResult.of(new PageImpl<>(personalizedResult, page.getPageable(), page.getTotalElements()));
    }

    public Page<BookSearchResponse> searchHybridCandidates(BookSearchRequest request, Pageable pageable) {
        long totalStart = System.currentTimeMillis();
        Pageable candidatePageable = hybridCandidatePageable(pageable);

        long keywordStart = System.currentTimeMillis();
        Page<BookSearchResponse> keywordResults =
                bookRepository.search(candidatePageable, request);
        long keywordElapsed = System.currentTimeMillis() - keywordStart;

        long embeddingStart = System.currentTimeMillis();
        float[] embedding = embeddingService.embed(request.keyword());
        long embeddingElapsed = System.currentTimeMillis() - embeddingStart;

        long vectorStart = System.currentTimeMillis();
        Page<BookSearchResponse> vectorResult =
                bookRepository.vectorSearch(
                        candidatePageable,
                        new BookSearchRequest(
                                request.keyword(),
                                request.isbn(),
                                SearchType.VECTOR,
                                embedding
                        )
                );
        long vectorElapsed = System.currentTimeMillis() - vectorStart;

        long mergeStart = System.currentTimeMillis();
        List<BookSearchResponse> rrf = rrfMerge(keywordResults, vectorResult, RRF_K);
        List<BookSearchResponse> content = pageContent(rrf, pageable);
        long mergeElapsed = System.currentTimeMillis() - mergeStart;

        long totalElements = Math.max(keywordResults.getTotalElements(), vectorResult.getTotalElements());

        log.info("[HYBRID] completed. elapsed={}ms, keyword={}ms({}/{}), embedding={}ms, vector={}ms({}/{}), merge={}ms, returned={}, pageable={}, candidatePageable={}",
                System.currentTimeMillis() - totalStart,
                keywordElapsed,
                keywordResults.getNumberOfElements(),
                keywordResults.getTotalElements(),
                embeddingElapsed,
                vectorElapsed,
                vectorResult.getNumberOfElements(),
                vectorResult.getTotalElements(),
                mergeElapsed,
                content.size(),
                pageable,
                candidatePageable);

        return new PageImpl<>(content, pageable, totalElements);
    }

    private Pageable hybridCandidatePageable(Pageable pageable) {
        long requestedEnd = pageable.getOffset() + pageable.getPageSize();
        int candidateSize = (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(requestedEnd, MIN_HYBRID_CANDIDATES)
        );
        return PageRequest.of(0, candidateSize);
    }

    private List<BookSearchResponse> pageContent(List<BookSearchResponse> books, Pageable pageable) {
        int start = (int) Math.min(pageable.getOffset(), books.size());
        int end = Math.min(start + pageable.getPageSize(), books.size());
        return books.subList(start, end);
    }


    private List<BookSearchResponse> rrfMerge(
            Page<BookSearchResponse> keywordResults,
            Page<BookSearchResponse> vectorResults,
            int rrfk
    ){
        Map<Long,BookSearchResponse> rrf = new HashMap<>();
        double result = 0;

        for(int i = 0; i < keywordResults.getContent().size(); i++) {
            BookSearchResponse bookSearchResponse = keywordResults.getContent().get(i);
            int rank = i + 1;

            double score = rrfScore(rank,rrfk);
            System.out.printf("키워드 검색 순위: %f",score);
            bookSearchResponse.setRrfScore(score);
            rrf.put(bookSearchResponse.getId(), bookSearchResponse);
        }
        for(int i = 0; i < vectorResults.getContent().size(); i++) {
            BookSearchResponse bookSearchResponse = vectorResults.getContent().get(i);
            int rank = i + 1;

            double score = rrfScore(rank,rrfk);
            System.out.printf("벡터 검색 순위: %f",score);
            if(rrf.containsKey(bookSearchResponse.getId())){
                double score1 = rrf.get(bookSearchResponse.getId()).getRrfScore();
                result = score1 + score;
                bookSearchResponse.setRrfScore(result);
                rrf.put(bookSearchResponse.getId(), bookSearchResponse);
            }else{
                bookSearchResponse.setRrfScore(score);
                rrf.put(bookSearchResponse.getId(), bookSearchResponse);
            }
        }
        return rrf.values().stream().sorted().toList().reversed();
    }
    private void mergeVectorMetadata(BookSearchResponse target, BookSearchResponse vectorBook) {
        if (target.getSimilarity() == null && vectorBook.getSimilarity() != null) {
            target.setSimilarity(vectorBook.getSimilarity());
        }
    }

    private double rrfScore(int rank, int k) {
        return 1.0 / (k + rank);
    }
}
