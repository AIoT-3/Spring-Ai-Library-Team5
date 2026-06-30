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

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("보내주신 메시지: " + messageText);

            try{
                execute(sendMessage);
            }catch (TelegramApiException e){
                log.error("[Telegram] 송신 실패");
            }

//            // Command 분기 처리
//            if (messageText.startsWith("/")) {
//                handleCommand(update, messageText);
//            } else {
//                // 일반 텍스트도 검색으로 처리
//                handleSearch(update, messageText);
//            }
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
        // Command 처리 로직
    }

    private void handleSearch(Update update, String keyword) {
        // RAG 검색 처리
    }
}
