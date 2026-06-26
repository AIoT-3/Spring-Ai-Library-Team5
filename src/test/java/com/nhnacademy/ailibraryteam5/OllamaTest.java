package com.nhnacademy.ailibraryteam5;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class OllamaTest {
    @Autowired
    OllamaChatModel chatModel; // 이제 이 콩(Bean)은 제미나이가 아니라 Ollama 엔진이 됩니다!

    @Test
    void ollamaTest() {
        String response = chatModel.call("안녕! 너는 로컬에서 돌아가고 있는 Ollama니?");
        log.info("Ollama의 답변: {}", response);
    }
}
