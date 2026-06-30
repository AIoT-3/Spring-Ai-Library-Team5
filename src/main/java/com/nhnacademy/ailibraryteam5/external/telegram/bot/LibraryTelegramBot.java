package com.nhnacademy.ailibraryteam5.external.telegram.bot;

import com.nhnacademy.ailibraryteam5.core.book.domain.SearchType;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchRequest;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResponse;
import com.nhnacademy.ailibraryteam5.core.book.dto.BookSearchResult;
import com.nhnacademy.ailibraryteam5.core.book.service.BookSearchService;
import com.nhnacademy.ailibraryteam5.external.telegram.config.TelegramBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

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
                sendSimpleMessage(chatId, MISSING_KEYWORD_MESSAGE);
            }
            return;
        }

        switch (command.trim()){
            case "/start" -> sendSimpleMessage(chatId, WELCOME_MESSAGE);
            case "/help" -> sendSimpleMessage(chatId, HELLP_COMMAND_MESSAGE);
            case "/search" -> sendSimpleMessage(chatId, MISSING_KEYWORD_MESSAGE);
            default -> sendSimpleMessage(chatId, UNKNOWN_COMMAND_MESSAGE);
        }
    }

    private void handleSearch(Update update, String keyword) {
        Long chatId = update.getMessage().getChatId();
        log.info("[Telegram] 도서 검색 시작 keyword: {}, chatId: {}", keyword, chatId);

        sendSimpleMessage(chatId, "도서 검색 중 : " + keyword);

        Pageable pageable = PageRequest.of(0, 5);
        BookSearchRequest request = new BookSearchRequest(keyword, null, SearchType.HYBRID, null, false);

        BookSearchResult result = bookSearchService.searchBooks(pageable, request);

        sendSearchResult(chatId, keyword, result);
    }

    private void sendSearchResult(Long chatId, String keyword, BookSearchResult result){
        List<BookSearchResponse> books = result.getBooks().getContent();
        if (books.isEmpty()) {
            sendSimpleMessage(chatId, "❌ 검색 결과가 없습니다.");
            return;
        }

        StringBuilder header = new StringBuilder();
        header.append("📚 \"").append(escapeMarkdown(keyword)).append("\" 검색 결과\n\n");

        int displayCount = books.size();
        header.append("검색된 도서 (").append(displayCount).append("개)\n\n");

        sendSimpleMessage(chatId, header.toString());

        for (int i = 0; i < books.size(); i++) {
            BookSearchResponse book = books.get(i);
            sendBookWithScore(chatId, keyword, i + 1, book);
        }
    }

    private void sendBookWithScore(Long chatId, String keyword, int index, BookSearchResponse book) {
        StringBuilder bookInfo = new StringBuilder();

        bookInfo.append(index).append(". ").append(book.getTitle()).append("\n");
        bookInfo.append("작가: ").append(book.getAuthorName()).append("\n");

        if (book.getPublisherName() != null) {
            bookInfo.append("출판사: ").append(book.getPublisherName()).append("\n");
        }

        if (book.getSimilarity() != null && book.getSimilarity() > 0) {
            bookInfo.append(String.format("유사도: %.2f%%\n", book.getSimilarity() * 100));
        }
        if (book.getRrfScore() != null && book.getRrfScore() > 0) {
            bookInfo.append(String.format("최종 점수: %.5f\n", book.getScore()));
        }

        bookInfo.append("상세 보기: http://127.0.0.1:8080/books/").append(book.getId()).append("\n");

        sendSimpleMessage(chatId, bookInfo.toString());
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

    /**
     * Markdown 특수문자 이스케이프 처리
     * Telegram API 오류를 방지하기 위해 특수문자를 제거합니다
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        // Markdown 특수문자 제거
        return text.replace("*", "")
                .replace("_", "")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
                .replace("~", "")
                .replace("`", "")
                .replace(">", "")
                .replace("#", "")
                .replace("+", "")
                .replace("-", "")
                .replace("=", "")
                .replace("|", "")
                .replace("{", "")
                .replace("}", "")
                .replace(".", "")
                .replace("!", "");
    }

    private static final String WELCOME_MESSAGE =
            """
                🎉 AI Library Bot에 오신 것을 환영합니다!

                이 Bot은 AI 기반 하이브리드 검색을 제공합니다.

                사용법:
                • 도서 제목이나 키워드를 입력하면 자동 검색됩니다
                • /search <키워드> Command로도 검색 가능합니다
                • /ai <메시지> - AI Agent가 복잡한 요청을 처리합니다
                • 자연어 검색도 지원합니다 (예: 해리포터 비슷한 책)

                도움이 필요하시면 /help를 입력하세요
                """;


    private static final String HELLP_COMMAND_MESSAGE = "도움말 ~~~";

    private static final String UNKNOWN_COMMAND_MESSAGE =
            "❌ 알 수 없는 Command입니다.\n\n도움이 필요하시면 /help를 입력하세요.";

    private static final String MISSING_KEYWORD_MESSAGE =
            "검색어를 입력해주세요.\n예: /search 해리포터";

}
