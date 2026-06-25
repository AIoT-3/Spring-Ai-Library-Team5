package com.nhnacademy.ailibraryteam5.core.book.service;

import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nhnacademy.ailibraryteam5.core.book.domain.SearchType.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookSearchService {

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return switch (request.searchType()){
            case KEYWORD -> keywordSearch(pageable,request);
            case VECTOR -> vectorSearch(pageable,request);
            case HYBRID -> hybridSearch(pageable, request); // 만든뒤 바꿔줘야함
            case RAG    -> keywordSearch(pageable, request);
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
        int retrievalCandidates = 100;
        if(request.keyword() == null || request.keyword().isBlank()){
            return keywordSearch(pageable, request);
        }
        Pageable cadidatePageable = PageRequest.of(0, retrievalCandidates);

        //키워드 검색 탑 100
        Page<BookSearchResponse> keywordResults = bookRepository.search(cadidatePageable,request);
        //임베딩
        float[] embedding = embeddingService.embed(request.keyword());
        //벡터 검색 탑 100
        Page<BookSearchResponse> vectorResults = bookRepository.vectorSearch(
                cadidatePageable
                ,new BookSearchRequest(
                        request.keyword(),
                        request.isbn(),
                        SearchType.VECTOR,
                        embedding
        ));
        List<BookSearchResponse> merged = rrfMerge(keywordResults,vectorResults,60);
        Page<BookSearchResponse> page = new PageImpl<>(merged,pageable,merged.size());
        return BookSearchResult.of(page);
    }
    private List<BookSearchResponse> rrfMerge(
            Page<BookSearchResponse> keywordResults,
            Page<BookSearchResponse> vectorResults,
            int rrfk
    ){
        Map<Long, BookSearchResponse> resultMap = new HashMap<>();
        Map<Long, Double> rrfScoreMap = new HashMap<>();

        //키워드 점수 계산
        for(int i = 0; i < keywordResults.getContent().size(); i++){
            BookSearchResponse book = keywordResults.getContent().get(i);
            Long bookId = book.getId();
            int rank = i + 1;
            resultMap.putIfAbsent(bookId, book);
            double score = rrfScore(rank,rrfk);
            rrfScoreMap.merge(bookId, score, Double::sum);
        }
        for(int i = 0; i < vectorResults.getContent().size(); i++){
            BookSearchResponse book = vectorResults.getContent().get(i);
            Long bookId = book.getId();
            int rank = i + 1;
            resultMap.putIfAbsent(bookId, book);
            double score = rrfScore(rank,rrfk);
            rrfScoreMap.merge(bookId, score, Double::sum);
        }
        for(Map.Entry<Long, BookSearchResponse> entry : resultMap.entrySet()){
            Long bookId = entry.getKey();
            BookSearchResponse book = entry.getValue();
            double rrfScore = rrfScoreMap.getOrDefault(bookId, 0.0);
            book.setRrfScore(rrfScore);
        }
        return resultMap.values().stream().sorted(Comparator.comparing(BookSearchResponse::getRrfScore).reversed()).toList();
    }
    private double rrfScore(int rank, int k) {
        return 1.0 / (k + rank);
    }
}
