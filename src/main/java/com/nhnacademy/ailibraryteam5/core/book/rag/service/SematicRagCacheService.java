package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.rag.cache.RagProperties;
import com.nhnacademy.ailibraryteam5.core.book.rag.cache.SemanticRagCacheEntry;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SematicRagCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties ragProperties;

    public Optional<List<BookAiRecommendationResponse>> findSimilar(BookSearchRequest request) {
        if (!ragProperties.getSemanticCache().isEnabled() || request.isWarmUp()) {
            return Optional.empty();
        }

        double bestScore = -1.0;

        try {
            String indexKey = indexKey(request);
            int limit = ragProperties.getSemanticCache().getMaxCandidatesToCompare();
            List<Object> keys = redisTemplate.opsForList().range(indexKey, 0, limit - 1L);
            if (keys == null || keys.isEmpty()) {
                return Optional.empty();
            }

            SemanticRagCacheEntry bestEntry = null;
            for (Object rawKey : keys) {
                String key = String.valueOf(rawKey);
                Object value = redisTemplate.opsForValue().get(key);
                if (!(value instanceof SemanticRagCacheEntry entry)) {
                    redisTemplate.opsForList().remove(indexKey, 0, key);
                    continue;
                }

                double score = VectorSimilarity.cosine(request.vector(), entry.embedding());
                double threshold = ragProperties.getSemanticCache().getSimilarityThreshold();
                if (score >= threshold) {
                    bestScore = score;
                    bestEntry = entry;
                }
                if (bestScore < score) {
                    bestScore = score;
                }
            }

            if (bestEntry != null) {
                log.info("[RAG_SEMANTIC_CACHE] hit. score={}, cachedKeyword={}", bestScore, bestEntry.normalizedKeyword());
                return Optional.of(bestEntry.recommend());
            }
            log.info("[RAG_SEMANTIC_CACHE] miss. bestScore={}", bestScore);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("[RAG_SEMANTIC_CACHE] lookup failed", e);
            return Optional.empty();
        }
    }

    public void save(BookSearchRequest request, float[] embedding, List<BookAiRecommendationResponse> recommend) {
        if (!cacheable(request, embedding, recommend)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String key = entryKey(request);
            String indexKey = indexKey(request);
            SemanticRagCacheEntry entry = new SemanticRagCacheEntry(
                    normalize(request.keyword()),
                    embedding,
                    recommend,
                    now
            );

            Duration ttl = Duration.ofMinutes(ragProperties.getSemanticCache().getTtlMinutes());
            redisTemplate.opsForValue().set(key, entry, ttl);
            redisTemplate.opsForList().leftPush(indexKey, key);
            trimIndex(indexKey);
        } catch (RuntimeException e) {
            log.warn("[RAG_SEMANTIC_CACHE] save failed. response will not fail.", e);
        }
    }

    private boolean cacheable(
            BookSearchRequest request,
            float[] queryEmbedding,
            List<BookAiRecommendationResponse> recommend
    ) {
        return ragProperties.getSemanticCache().isEnabled()
                && !request.isWarmUp()
                && queryEmbedding != null
                && queryEmbedding.length > 0
                && recommend != null
                && !recommend.isEmpty();
    }

    private String entryKey(BookSearchRequest request) {
        return namespace("entry", request) + ":" + UUID.randomUUID();
    }

    private String indexKey(BookSearchRequest request) {
        return namespace("index", request);
    }

    private String namespace(String type, BookSearchRequest request) {
        return ragProperties.getSemanticCache().getVersion()
                + ":" + type
                + ":k" + ragProperties.getCandidates()
                + ":isbn:" + isbnNamespace(request.isbn());
    }

    private String isbnNamespace(String isbn) {
        String normalized = normalize(isbn);
        return normalized.isBlank() ? "all" : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("\\s+", " ").toLowerCase();
    }

    private void trimIndex(String indexKey) {
        int maxIndexSize = ragProperties.getSemanticCache().getMaxIndexSize();
        redisTemplate.opsForList().trim(indexKey, 0, maxIndexSize - 1);
    }
}
