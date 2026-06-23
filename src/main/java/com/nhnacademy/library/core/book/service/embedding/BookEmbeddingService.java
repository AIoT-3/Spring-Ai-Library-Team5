package com.nhnacademy.library.core.book.service.embedding;

import com.nhnacademy.library.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookEmbeddingService {
    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
}
