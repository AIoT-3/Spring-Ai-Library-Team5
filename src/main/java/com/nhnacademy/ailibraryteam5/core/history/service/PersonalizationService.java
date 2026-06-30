package com.nhnacademy.ailibraryteam5.core.history.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.core.book.util.VectorUtils;
import com.nhnacademy.ailibraryteam5.core.history.domain.BookViewHistory;
import com.nhnacademy.ailibraryteam5.core.history.repository.BookViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
                .collect(Collectors.toList());
    }

    @Transactional
    public Long userSavedBook(String userId, Long bookId){
        log.info("유저가 살펴본 도서 저장 userId : {}, bookId : {}", userId, bookId);
        BookViewHistory history = new BookViewHistory(userId, bookId);
        BookViewHistory savedHistory = historyRepository.save(history);

        return savedHistory.getId();
    }

}
