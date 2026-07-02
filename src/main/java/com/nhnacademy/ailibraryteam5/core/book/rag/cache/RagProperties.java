package com.nhnacademy.ailibraryteam5.core.book.rag.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int candidates = 10;
    private SemanticCache semanticCache = new SemanticCache();

    @Getter
    @Setter
    public static class SemanticCache {
        private boolean enabled = true;
        private long ttlMinutes = 100;
        private double similarityThreshold = 0.8;
        private int maxCandidatesToCompare = 5;
        private int maxIndexSize = 2000;
        private String version = "rag-semantic:v1";
    }
}
