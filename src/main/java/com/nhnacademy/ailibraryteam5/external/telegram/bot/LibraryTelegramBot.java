package com.nhnacademy.ailibraryteam5.external.telegram.bot;

import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.external.telegram.config.TelegramBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
public class LibraryTelegramBot extends TelegramLongPollingBot {

    private final TelegramBotProperties properties;
    private final BookSearchService bookSearchService;

    public LibraryTelegramBot(TelegramBotProperties properties, DefaultBotOptions options, BookSearchService bookSearchService) {
        super(options);
        this.properties = properties;
        this.bookSearchService = bookSearchService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            log.info("[Telegram] 수신 내용: {} chatId: {}", messageText, chatId);

            if (messageText.startsWith("/")) {
                handleCommand(update, messageText);
            } else {
                handleSearch(update, messageText);
            }

        }
    }

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    private void handleCommand(Update update, String command) {
        Long chatId = update.getMessage().getChatId();

        if(command.startsWith("/search ")){
            String keyword = command.substring("/search ".length()).trim();
            if(!keyword.isEmpty()){
                handleSearch(update, keyword);
            }else {
                sendSimpleMessage(chatId, "검색어를 입력해주세요.\n예: /search 해리포터");
            }
            return;
        }

        switch (command.trim()){
            case "/help" -> sendSimpleMessage(chatId, hellpCommandMessage());
            default -> sendSimpleMessage(chatId, unknownCommandMessage());
        }
    }

    private void handleSearch(Update update, String keyword) {
        // RAG 검색 처리
    }

    private void sendSimpleMessage(Long chatId, String text){
        if (text == null || text.isBlank()) {
            log.warn("[Telegram] 빈 메시지 스킵 chatId {}", chatId);
            return;
        }

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();

        try{
            execute(message);
            log.info("[Telegram] 메시지 송신 성공 chatId {}", chatId);
        } catch (TelegramApiException e) {
            log.error("[Telegram] 메시지 송신 실패 chatId {}: {}", chatId, e.getMessage());
        }
    }

    private String welcomeMessage(){
        return """
                🎉 AI Library Bot에 오신 것을 환영합니다!

                이 Bot은 AI 기반 하이브리드 검색을 제공합니다.

                사용법:
                • 도서 제목이나 키워드를 입력하면 자동 검색됩니다
                • /search 키워드 Command로도 검색 가능합니다
                • /ai 메시지 - AI Agent가 복잡한 요청을 처리합니다
                • 자연어 검색도 지원합니다 (예: 해리포터 비슷한 책)

                Step 7 새로운 기능:
                • AI Agent가 도서 검색, 리뷰 조회, 대출 가능 확인을 한 번에!
                • /ai 자바 책 추천해줘
                • /ai 클린 코드 리뷰랑 대출 가능한 곳 알려줘

                도움이 필요하시면 /help를 입력하세요
                """;
    }

    private String hellpCommandMessage(){
        return "도움말 ~~~";
    }

    private String unknownCommandMessage(){
        return "❌ 알 수 없는 Command입니다.\n\n도움이 필요하시면 /help를 입력하세요.";
    }
}
