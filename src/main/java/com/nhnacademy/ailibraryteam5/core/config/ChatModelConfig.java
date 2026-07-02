package com.nhnacademy.ailibraryteam5.core.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatModelConfig {

    @Value("${spring.ai.selected-model:ollama}")
    private String selectedModel;

    @Bean
    @Primary
    public ChatModel primaryChatModel(ApplicationContext context) {
        if ("gemini".equalsIgnoreCase(selectedModel)) {
            return context.getBean("googleGenAiChatModel", ChatModel.class);
        } else if ("openai".equalsIgnoreCase(selectedModel)) {
            return context.getBean("openAiChatModel", ChatModel.class);
        } else {
            return context.getBean("ollamaChatModel", ChatModel.class);
        }
    }
}
