package com.nhnacademy.library.ai.agent.orchestrator;

import com.nhnacademy.library.ai.agent.QueryParserAgent;
import com.nhnacademy.library.ai.agent.dto.ParsedQuery;
import com.nhnacademy.library.ai.function.LibraryTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatMemory;
import org.springframework.ai.chat.prompt.PromptChatMemoryAdvisor;
import org.springframework.stereotype.Service;

/**
 * AI Orchestration Service - @Tool 애노테이션 방식
 * <p>
 * Spring AI 1.1.2의 @Tool 애노테이션을 활용한 간결한 Function Calling 구현입니다.
 * 기존 FunctionToolCallback.builder() 방식보다 코드가 훨씬 간결합니다.
 * </p>
 *
 * <p><b>주요 변경사항:</b></p>
 * <ul>
 *   <li>FunctionToolCallback.builder() → @Tool 애노테이션</li>
 *   <li>명시적인 Tool 등록 → .defaultTools() 자동 등록</li>
 *   <li>DTO 클래스 → @ToolParam으로 직접 설명</li>
 *   <li>Chat Memory 추가 (선택)</li>
 * </ul>
 *
 * <p><b>아키텍처:</b></p>
 * <ol>
 *   <li><b>QueryParserAgent (llama3-korean-blossom)</b>: 쿼리 파싱 및 엔티티 추출</li>
 *   <li><b>LibraryTools (@Tool)</b>: 도서관 API 기능 제공</li>
 *   <li><b>LLM (qwen2.5)</b>: 필요한 Tool 스스로 선택 및 실행</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolBasedAiOrchestrationService {

    private final ChatModel chatModel;
    private final QueryParserAgent queryParserAgent;
    private final LibraryTools libraryTools;
    private final ChatMemory chatMemory;  // 선택사항: 대화 기록 유지

    /**
     * 사용자 요청을 처리하고 AI 응답을 반환합니다.
     * <p>
     * <b>실행 흐름:</b>
     * </p>
     * <ol>
     *   <li>QueryParserAgent로 쿼리 파싱 (엔티티 추출)</li>
 *   *   <li>ChatClient 생성 (@Tool 자동 등록)</li>
     *   <li>LLM이 필요한 Tool 스스로 선택 및 실행</li>
     *   <li>결과 종합 및 자연어 응답 생성</li>
     * </ol>
     *
     * @param userMessage 사용자 메시지
     * @return AI 응답
     */
    public String ask(String userMessage) {
        long startTime = System.currentTimeMillis();
        log.info("[MainAgent] 사용자 요청 수신: {}", userMessage);

        try {
            // 1. QueryParserAgent로 쿼리 파싱 (llama3-korean-blossom)
            log.info("[MainAgent] Step 1: 쿼리 파싱 시작");
            ParsedQuery parsed = queryParserAgent.parse(userMessage);

            if (!parsed.isValid()) {
                log.warn("[MainAgent] 파싱 실패: 유효하지 않은 쿼리");
                return "질문을 이해하지 못했습니다. 도서 검색, 도서관 조회 등으로 질문해주세요.";
            }

            // 2. ChatClient 생성 (@Tool 자동 등록)
            log.info("[MainAgent] Step 2: ChatClient 생성 (@Tool 자동 등록)");
            ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(libraryTools)  // ✅ @Tool 메서드 자동 등록
                .defaultAdvisors(
                    new PromptChatMemoryAdvisor(chatMemory)  // ✅ 대화 기록 유지
                )
                .build();

            // 3. System Prompt
            log.info("[MainAgent] Step 3: System Prompt 생성");
            String systemPrompt = buildSystemPrompt(parsed);

            // 4. LLM 호출 (qwen2.5)
            log.info("[MainAgent] Step 4: LLM 호출 시작 (qwen2.5)");
            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

            // 5. 총 소요시간
            long totalElapsed = System.currentTimeMillis() - startTime;
            log.info("[MainAgent] 응답 생성 완료 (총 {}ms)", totalElapsed);

            return response;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[MainAgent] 요청 처리 실패 ({}ms)", elapsed, e);
            return "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.";
        }
    }

    /**
     * System Prompt 생성 (Telegram 메신저에 최적화)
     */
    private String buildSystemPrompt(ParsedQuery parsed) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 도서관 도우미 AI입니다.\n\n");

        // 파싱된 정보가 있으면 힌트 제공
        if (parsed.hasBookName()) {
            prompt.append(String.format("검색 도서: %s\n\n", parsed.bookName()));
        }
        if (parsed.hasRegionName() && parsed.regionCode() != null) {
            prompt.append(String.format("**지역 정보:** %s (지역 코드: %s)\n", parsed.regionName(), parsed.regionCode()));
            prompt.append(String.format("**⚠️ 매우 중요:** searchLibraries 함수를 호출할 때 반드시 지역 코드 \"%s\"를 사용하세요.\n", parsed.regionCode()));
            prompt.append(String.format("절대 한글 지역명(\"%s\")을 region 파라미터에 넣지 마세요. API 에러가 발생합니다.\n\n", parsed.regionName()));
        }

        prompt.append("**중요한 지침:**\n");
        prompt.append("1. 반드시 한국어로 답변하세요.\n");
        prompt.append("2. Telegram 메신저에 맞춰 간단 명료하게 작성하세요.\n");
        prompt.append("3. 제목(#, ##) 대신 볼드(*text*)를 사용하세요.\n");
        prompt.append("4. 줄바꿈을 적절히 사용하여 가독성을 높이세요.\n\n");

        prompt.append("**도구 호출 가이드:**\n");
        prompt.append("- searchBooks: 도서 제목으로 검색하여 ISBN 확보\n");
        prompt.append("- searchLibraries: 지역 코드(숫자)로 도서관 검색 (한글 지역명 사용 금지!)\n");
        prompt.append("- checkBookExists: 도서관 코드와 ISBN으로 소장 여부 확인\n");
        prompt.append("- checkLoanAvailability: ISBN으로 대출 가능한 도서관 검색\n\n");

        prompt.append("**중요:** 도서관이 많을 경우는 최대 10~15개만 보여주고, ");
        prompt.append("\"나머지 도서관이 있습니다. 전체 목록이 필요하시면 말씀해주세요.\"라고 안내해 주세요.\n\n");

        return prompt.toString();
    }
}

/**
 * ChatMemory 빈 설정 (예시)
 * <p>
 * application.properties 또는 @Configuration 클래스에서 설정:
 * </p>
 * <pre>
 * &#64;Bean
 * public ChatMemory chatMemory() {
 *     return new InMemoryChatMemory();
 * }
 * </pre>
 *
 * <p>또는 application.properties:</p>
 * <pre>
 * spring.ai.chat.memory.enabled=true
 * spring.ai.chat.memory.in-memory.enabled=true
 * </pre>
 */
