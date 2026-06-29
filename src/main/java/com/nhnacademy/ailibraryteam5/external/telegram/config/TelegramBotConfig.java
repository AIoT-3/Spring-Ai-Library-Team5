package com.nhnacademy.ailibraryteam5.external.telegram.config;

import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.external.telegram.bot.LibraryTelegramBot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class TelegramBotConfig {

    private final TelegramBotProperties properties;

    private BotSession botSession;

    @Bean
    public LibraryTelegramBot libraryTelegramBot(
            BookSearchService bookSearchService
    ){
        DefaultBotOptions options = new DefaultBotOptions();
        return new LibraryTelegramBot(properties, options, bookSearchService);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBot(ApplicationReadyEvent event) {
        LibraryTelegramBot bot = event.getApplicationContext().getBean(LibraryTelegramBot.class);
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botSession = botsApi.registerBot(bot);
            log.info("[Telegram] 봇 생성 성공: @{}", properties.getUsername());
        } catch (TelegramApiException e) {
            log.error("[Telegram] 봇 생성 실패", e);
        }
    }

    @PreDestroy
    public void stopBot() {
        if (botSession != null && botSession.isRunning()) {
            try {
                botSession.stop();
                log.info("[Telegram] 봇 정지 성공");
            } catch (Exception e) {
                log.error("[Telegram] 봇 정지 실패", e);
            }
        }
    }
}