package com.nhnacademy.ailibrarymyself.core.book.service;

import com.nhnacademy.ailibrarymyself.core.book.processor.TextPreProcessor;
import com.nhnacademy.ailibrarymyself.core.book.repository.BookRepository;
import com.nhnacademy.ailibrarymyself.core.builder.BookEmbeddingTextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookEmbeddingBatchService {
    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final BookEmbeddingTextBuilder textBuilder;
    private final TextPreProcessor preProcessor;

    @Value("${app.batch.embedding-size:32}")
    private int batchSize;

    @Transactional
    public int processNextBatch(){
return 0;
    }
}
