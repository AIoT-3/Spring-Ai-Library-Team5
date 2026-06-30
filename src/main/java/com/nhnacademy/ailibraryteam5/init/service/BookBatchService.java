package com.nhnacademy.ailibraryteam5.init.service;

import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import com.nhnacademy.ailibraryteam5.core.book.repository.BookRepository;
import com.nhnacademy.ailibraryteam5.init.dto.BookRawData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookBatchService {
    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public boolean hasBooks() {
        return bookRepository.count() > 0;
    }

    @Transactional
    public void deleteAllBooks() {
        log.warn("book init reset enabled. delete all books before loading.");
        bookRepository.deleteAllInBatch();
        bookRepository.flush();
    }

    @Transactional
    public void saveBooks(List<BookRawData> books) {
        bookRepository.saveAll(books.stream().map(this::toBook).toList());
        bookRepository.flush();
    }

    private Book toBook(BookRawData item) {
        return new Book(
                item.getIsbn(),
                item.getTitle(),
                item.getAuthorName(),
                item.getPublisherName(),
                item.getFirstPublishDate(),
                item.getPrice(),
                item.getImageUrl(),
                item.getBookContent(),
                item.getCategory(),
                item.getSubtitle(),
                item.getEditionPublishDate()
        );
    }
}
