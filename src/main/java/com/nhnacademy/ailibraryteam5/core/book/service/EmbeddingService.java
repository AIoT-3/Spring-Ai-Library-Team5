package com.nhnacademy.ailibraryteam5.core.book.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text){
        if(text == null || text.isBlank()){
            log.info("[EMBEDDING] skipped blank text");
            return new float[]{};
        }
        long start = System.currentTimeMillis();
        float[] embed = embeddingModel.embed(text);
        log.info("[EMBEDDING] single completed. elapsed={}ms, textLength={}, dimension={}",
                System.currentTimeMillis() - start,
                text.length(),
                embed.length);
        if(embed.length != 1024){
            throw new IllegalArgumentException();
        }
        return embed;
    }
    public List<float[]> embeds(List<String> texts){
        long start = System.currentTimeMillis();
        List<float[]> result = embeddingModel.embed(texts);
        log.info("[EMBEDDING] batch completed. elapsed={}ms, count={}",
                System.currentTimeMillis() - start,
                texts == null ? 0 : texts.size());
        return result;
    }
}
