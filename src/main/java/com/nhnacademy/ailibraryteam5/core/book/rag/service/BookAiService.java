package com.nhnacademy.ailibraryteam5.core.book.rag.service;

import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.rag.parser.RagResponseParser;
import com.nhnacademy.ailibraryteam5.core.book.rag.dto.BookAiRecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class BookAiService {

    private final ChatClient chatClient;
    private final RagResponseParser ragResponseParser;


    public BookAiService(
            @Qualifier("geminiClient") ChatClient chatClient,
            RagResponseParser ragResponseParser
    ) {
        this.chatClient = chatClient;
        this.ragResponseParser = ragResponseParser;
    }

    public List<BookAiRecommendationResponse> call(String prompt, List<BookSearchResponse> candidates){

        long start = System.currentTimeMillis();
        try{
            String rawResponse = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("LLM call completed. elapsed={}ms, promptLength={}, rawLength={}",
                    System.currentTimeMillis() - start,
                    prompt == null ? 0 : prompt.length(),
                    rawResponse == null ? 0 : rawResponse.length());
            return ragResponseParser.parse(rawResponse, candidates);
        }catch(Exception e){
            log.warn("LLM call failed. elapsed={}ms, promptLength={}",
                    System.currentTimeMillis() - start,
                    prompt == null ? 0 : prompt.length(), e);
            throw new RuntimeException(e);
        }
    }
}
