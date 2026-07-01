package com.nhnacademy.ailibraryteam5.core.book.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    @Bean(name = "ollamaClient")
    @Primary
    public ChatClient ollamaClient(@Qualifier("ollamaChatModel") ChatModel ollamaChatModel){
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        new MetricsAdvisor(),
                        new SimpleLoggerAdvisor()
                ).build();
    }
    @Bean(name = "geminiClient")
    public ChatClient geminiClient(@Qualifier("googleGenAiChatModel") ChatModel chatModel){
        return ChatClient.builder(chatModel).
                defaultAdvisors(
                        new MetricsAdvisor(),
                        new SimpleLoggerAdvisor()
                ).build();
    }
}
