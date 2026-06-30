package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BookAiService {

    private final ChatClient chatClient;


    public BookAiService(@Qualifier("ollamaClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public List<BookAiRecommendationResponse> call(String prompt){

        long start = System.currentTimeMillis();
        try{
            List<BookAiRecommendationResponse> result = chatClient
                    .prompt()
                            .user(prompt)
                                    .call()
                    .entity(new ParameterizedTypeReference<List<BookAiRecommendationResponse>>() {});

            log.info("LLM call completed. elapsed={}ms, promptLength={}, responseLength={}",
                    System.currentTimeMillis() - start,
                    prompt == null ? 0 : prompt.length(),
                    result == null ? 0 : result.size());
            return result;
        }catch(Exception e){
            log.warn("LLM call failed. elapsed={}ms, promptLength={}",
                    System.currentTimeMillis() - start,
                    prompt == null ? 0 : prompt.length(), e);
            throw new RuntimeException(e);
        }
    }
}
