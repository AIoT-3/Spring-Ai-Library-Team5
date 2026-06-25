package com.nhnacademy.ailibraryteam5.init.parser;

import com.nhnacademy.ailibraryteam5.init.dto.BookRawData;
import com.nhnacademy.ailibraryteam5.init.properties.InitProperties;
import com.nhnacademy.ailibraryteam5.init.service.BookBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvBookParser {
    private static final String CSV_HEADER_SEQ = "SEQ_NO";
    private static final String CSV_HEADER_ISBN = "ISBN_THIRTEEN_NO";
    private static final String CSV_HEADER_TITLE = "TITLE_NM";
    private static final String CSV_HEADER_AUTHOR = "AUTHR_NM";
    private static final String CSV_HEADER_PUBLISHER = "PUBLISHER_NM";
    private static final String CSV_HEADER_PUB_DATE = "PBLICTE_DE";
    private static final String CSV_HEADER_PRICE = "PRC_VALUE";
    private static final String CSV_HEADER_IMAGE = "IMAGE_URL";
    private static final String CSV_HEADER_CONTENT = "BOOK_INTRCN_CN";
    private static final String CSV_HEADER_CATEGORY = "KDC_NM";
    private static final String CSV_HEADER_SUBTITLE = "TITLE_SBST_NM";
    private static final String CSV_HEADER_EDITION_DATE = "TWO_PBLICTE_DE";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InitProperties initProperties;
    private final BookBatchService bookBatchService;

    public void parse() throws IOException {
        int batchSize = initProperties.getBatchSize();
        if (batchSize <= 0) {
            throw new IllegalArgumentException("init.batch_size는 1 이상이어야 합니다.");
        }

        String file = initProperties.getBookFile();
        log.info("book csv init started. file={}, batchSize={}, resetBeforeLoad={}",
                file, batchSize, initProperties.isResetBeforeLoad());

        if (initProperties.isResetBeforeLoad()) {
            bookBatchService.deleteAllBooks();
        } else if (bookBatchService.hasBooks()) {
            log.info("book csv init skipped. books already exist and init.reset_before_load=false");
            return;
        }

        List<BookRawData> buffer = new ArrayList<>(batchSize);
        long readCount = 0;
        long savedCount = 0;
        long skippedCount = 0;

        try (Reader reader = new InputStreamReader(
                BOMInputStream.builder()
                        .setInputStream(new ClassPathResource(file).getInputStream())
                        .get(),
                StandardCharsets.UTF_8
        );
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)
        ) {
            for (CSVRecord record : parser) {
                readCount++;
                BookRawData book = mapToBookRawData(record);

                if (!isValid(book)) {
                    skippedCount++;
                    continue;
                }

                buffer.add(book);
                if (buffer.size() == batchSize) {
                    bookBatchService.saveBooks(buffer);
                    savedCount += buffer.size();
                    buffer.clear();
                    log.info("book csv init progress. readCount={}, savedCount={}, skippedCount={}",
                            readCount, savedCount, skippedCount);
                }
            }

            if (!buffer.isEmpty()) {
                bookBatchService.saveBooks(buffer);
                savedCount += buffer.size();
            }
        }

        log.info("book csv init finished. file={}, readCount={}, savedCount={}, skippedCount={}",
                file, readCount, savedCount, skippedCount);
    }

    private BookRawData mapToBookRawData(CSVRecord record) {
        BookRawData book = new BookRawData();
        book.setId(parseLong(record.get(CSV_HEADER_SEQ)));
        book.setIsbn(trimToNull(record.get(CSV_HEADER_ISBN)));
        book.setTitle(trimToNull(record.get(CSV_HEADER_TITLE)));
        book.setAuthorName(trimToNull(record.get(CSV_HEADER_AUTHOR)));
        book.setPublisherName(trimToNull(record.get(CSV_HEADER_PUBLISHER)));
        book.setFirstPublishDate(parseDate(record.get(CSV_HEADER_PUB_DATE)));
        book.setPrice(parseBigDecimal(record.get(CSV_HEADER_PRICE)));
        book.setImageUrl(trimToNull(record.get(CSV_HEADER_IMAGE)));
        book.setBookContent(trimToNull(record.get(CSV_HEADER_CONTENT)));
        book.setCategory(trimToNull(record.get(CSV_HEADER_CATEGORY)));
        book.setSubtitle(trimToNull(record.get(CSV_HEADER_SUBTITLE)));
        book.setEditionPublishDate(parseDate(record.get(CSV_HEADER_EDITION_DATE)));
        return book;
    }

    private static boolean isValid(BookRawData book) {
        return book.getIsbn() != null && book.getTitle() != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(normalized, BASIC_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
