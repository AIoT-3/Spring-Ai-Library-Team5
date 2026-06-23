package com.nhnacademy.library.core.book.service.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public float[] getEmbedding(String text){
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().getFirst().getOutput();
    }

    public List<float[]> getEmbeddings(List<String> texts){
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }
}
