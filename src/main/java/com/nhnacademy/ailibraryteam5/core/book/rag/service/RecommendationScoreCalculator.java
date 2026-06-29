package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScoreCalculator {
    private static final double SIMILARITY_WEIGHT = 0.20;
    private static final double RRF_WEIGHT = 0.80;

    public Double calculate(
            Double similarity,
            Double rrfScore,
            double maxRrfScore,
            BookSearchResponse book,
            BookSearchRequest request
    ) {
        if (exactMatch(book, request)) {
            return 100.0;
        }
        return calculate(similarity, rrfScore, maxRrfScore);
    }

    public Double calculate(
            Double similarity,
            Double rrfScore,
            double maxRrfScore
    ) {
        if (similarity == null && rrfScore == null) {
            return null;
        }

        double similarityPercent = similarity == null ? 0.0 : similarity * 100.0;
        double rrfPercent = maxRrfScore <= 0.0 || rrfScore == null
                ? 0.0
                : rrfScore / maxRrfScore * 100.0;

        return similarityPercent * SIMILARITY_WEIGHT
                + rrfPercent * RRF_WEIGHT;
    }

    private boolean exactMatch(BookSearchResponse book, BookSearchRequest request) {
        if (book == null || request == null) {
            return false;
        }

        if (StringUtils.isNotBlank(request.isbn())
                && StringUtils.equalsIgnoreCase(request.isbn().strip(), safe(book.getIsbn()))) {
            return true;
        }

        return StringUtils.isNotBlank(request.keyword())
                && normalize(request.keyword()).equals(normalize(book.getTitle()));
    }

    private String normalize(String value) {
        return safe(value).replaceAll("\\s+", "").toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
