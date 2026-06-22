package com.nhnacademy.library.batch.init.service;

import com.nhnacademy.library.batch.init.dto.BookRawData;
import com.nhnacademy.library.core.book.domain.Book;
import com.nhnacademy.library.core.book.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookBatchService {
    private final BookRepository bookRepository;

    @Transactional
    public void initializeBooks(List<BookRawData> bookRawDataList){
        int batchSize = 1000;
        int total = bookRawDataList.size();

        log.info("도서 {}권을 배치 크기 {}로 저장 시작", total, batchSize);

        for(int i = 0; i < total; i += batchSize){

            int end = Math.min(i + batchSize, total);

            List<BookRawData> subList = bookRawDataList.subList(i, end);

            List<Book> books = convertToEntities(subList);

            bookRepository.saveAll(books);

            log.info("{} / {} 저장 완료", end, total);
        }
    }

    private List<Book> convertToEntities(List<BookRawData> rawDataList){
        List<Book> books = new ArrayList<>();

        for(BookRawData rawData : rawDataList){
            Book book = new Book(
                    rawData.getId(),
                    rawData.getIsbn(),
                    rawData.getVolumeTitle(),
                    rawData.getTitle(),
                    rawData.getAuthorName(),
                    rawData.getPublisherName(),
                    rawData.getFirstPublishDate(),
                    rawData.getPrice(),
                    rawData.getImageUrl(),
                    rawData.getBookContent(),
                    rawData.getSubtitle(),
                    rawData.getEditionPublishDate()
            );

            books.add(book);
        }
        return books;
    }
}
