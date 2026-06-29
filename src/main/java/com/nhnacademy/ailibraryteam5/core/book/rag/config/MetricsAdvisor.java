package com.nhnacademy.ailibraryteam5.core.book.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

@Slf4j
public class MetricsAdvisor implements CallAdvisor {

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();

        ChatClientResponse response = chain.nextCall(request);

        long duration = System.currentTimeMillis() - start;//문제있음

        if (response.chatResponse() != null) {
            var metadata = response.chatResponse().getMetadata();

            log.info("LLM 실행 시간: {}ms", duration);
            log.info("모델: {}", metadata.getModel());
            log.info("토큰 사용량: {}", metadata.getUsage());
        }

        return response;
    }

    @Override
    public @NonNull String getName() {
        return "MetricsAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
