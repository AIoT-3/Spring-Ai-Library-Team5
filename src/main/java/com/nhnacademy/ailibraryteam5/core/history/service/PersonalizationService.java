package com.nhnacademy.ailibraryteam5.core.history.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.book.util.VectorUtils;
import com.nhnacademy.ailibraryteam5.core.history.domain.BookViewHistory;
import com.nhnacademy.ailibraryteam5.core.history.repository.BookViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalizationService {
    private final BookViewHistoryRepository historyRepository;
    private final BookRepository bookRepository;

    private static final double PERSONALIZATION_WEIGHT = 0.3;
    public static final String TEMP_USER_ID = "tempUserId";

    @Transactional(readOnly = true)
    public float[] calculateUserPreferenceVector(String userId){
        log.info("유저 선호 벡터 계산 : {}", userId);

        List<Long> bookIds = historyRepository.findRecentBookIds(userId);

        if(bookIds.size() < 3){
            log.info("내역이 충분하지 않습니다 : {} (userId: {})", bookIds.size(), userId);
            return null;
        }

        List<float[]> embeddings = bookRepository.findEmbeddingsByIds(bookIds);

        if (embeddings.isEmpty()) {
            log.warn("임베딩을 찾을 수 없습니다. : {}", bookIds);
            return null;
        }

        return VectorUtils.AverageVector(embeddings);
    }

    public List<BookSearchResponse> personalizeSort(
            List<BookSearchResponse> results,
            String userId
    ){
        log.info("개인화 재정렬 시작");
        float[] userPreferenceVector = calculateUserPreferenceVector(userId);
        if(userPreferenceVector == null){
            log.info("선호벡터 없음 개인화 스킵 userId: {}", userId);
            return results;
        }

        return results.stream()
                .map(result -> {
                    float[] bookEmbedding = result.getEmbedding();
                    double similarity = VectorUtils.cosineSimilarity(
                            userPreferenceVector,
                            bookEmbedding
                    );

                    double rrfScore = result.getRrfScore();
                    double finalScore = rrfScore + (similarity * PERSONALIZATION_WEIGHT);

                    result.setScore(finalScore);
                    result.setPersonalizationScore(similarity);

                    return result;
                })
                .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                .toList();
    }

    @Transactional
    public Long userSavedBook(String userId, Long bookId){
        log.info("유저가 조회한 도서 기록 userId : {}, bookId : {}", userId, bookId);
        Optional<BookViewHistory> history = historyRepository.findByUserIdAndBookId(userId, bookId);

        if(history.isPresent()){
            history.get().updateViewAt();
            return history.get().getId();
        }

        BookViewHistory newHistory = new BookViewHistory(userId, bookId);
        return historyRepository.save(newHistory).getId();
    }

    @Transactional
    public void userDeleteBook(Long historyId){
        log.info("도서 조회 기록 삭제 historyId {}", historyId);

        historyRepository.deleteById(historyId);
    }

    @Transactional
    public void userDeleteAllBook(String userId){
        log.info("도서 조회 기록 전체 삭제 userId: {}", userId);

        historyRepository.deleteAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<BookViewHistory> getRecentViewHistories(String userId, Pageable pageable){
        log.info("도서 조회 기록 페이징 조회 userId: {}, pageable: {}", userId, pageable);

        List<BookViewHistory> historyList = historyRepository.findByUserId(userId, pageable);

        long totalCount = historyRepository.countByUserId(userId);

        log.info("최근 도서 조회 기록 count: {}", totalCount);

        return new PageImpl<>(historyList, pageable, totalCount);
    }

}
