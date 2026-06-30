package com.nhnacademy.ailibraryteam5.core.book.service;

import com.nhnacademy.ailibraryteam5.core.book.builder.BookEmbeddingTextBuilder;
import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import com.nhnacademy.ailibraryteam5.core.book.processor.TextPreProcessor;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookEmbeddingService {
    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final BookEmbeddingTextBuilder textBuilder;
    private final TextPreProcessor preProcessor;

    @Transactional
    public int generateEmbedding(int batchSize) {
        List<Book> books = bookRepository.findByEmbeddingIsNullOrderByIdAsc(PageRequest.of(0, batchSize));

        if (books.isEmpty()) {
            log.info("임베딩을 생성할 도서가 없습니다.");
            return 0;
        }

        log.info("임베딩 생성 시작: {}권", books.size());
        int updatedCount = 0;

        for (Book book : books) {
            try {
                String embeddingText = preProcessor.clean(textBuilder.build(book));
                float[] embedding = embeddingService.embed(embeddingText);

                if (embedding.length == 0) {
                    log.warn("임베딩 생성 건너뜀: 유효한 텍스트 없음, bookId={}", book.getId());
                    continue;
                }

                book.updateEmbedding(embedding);
                updatedCount++;
                log.debug("임베딩 생성 완료: {} (차원: {})", book.getTitle(), embedding.length);
            } catch (RuntimeException e) {
                log.warn("임베딩 생성 실패: bookId={}, isbn={}, reason={}", book.getId(), book.getIsbn(), e.getMessage(), e);
            }
        }

        bookRepository.flush();
        log.info("임베딩 생성 완료: targetCount={}, updatedCount={}", books.size(), updatedCount);
        return updatedCount;
    }
}
