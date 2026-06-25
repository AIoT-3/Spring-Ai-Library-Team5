package com.nhnacademy.ailibraryteam5.core.book.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text){
        if(text == null || text.isBlank()){
            return new float[]{};
        }
        float[] embed = embeddingModel.embed(text);
        if(embed.length != 1024){
            throw new IllegalArgumentException();
        }
        return embed;
    }
    public List<float[]> embeds(List<String> texts){
        return embeddingModel.embed(texts);
    }
}
