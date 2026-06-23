package com.nhnacademy.ailibrarymyself.core.book.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text){
        if(text.isBlank()){
            return new float[]{};
        }
        float[] embed = embeddingModel.embed(text);
        if(embed.length != 1024){
            throw new IllegalArgumentException();
        }
        return embed;
    }
}
