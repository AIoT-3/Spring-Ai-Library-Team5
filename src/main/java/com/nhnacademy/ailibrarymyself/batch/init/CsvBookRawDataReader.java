package com.nhnacademy.ailibrarymyself.batch.init;

import com.nhnacademy.ailibrarymyself.batch.init.dto.BookRawData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

@Slf4j
public class CsvBookRawDataReader implements ItemStreamReader<BookRawData> {

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

    private final String file;
    private Reader reader;
    private CSVParser csvParser;
    private Iterator<CSVRecord> iterator;
    private long readCount;

    public CsvBookRawDataReader(String file) {
        this.file = file;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            reader = new InputStreamReader(
                    BOMInputStream.builder()
                            .setInputStream(new ClassPathResource(file).getInputStream())
                            .get(),
                    StandardCharsets.UTF_8
            );

            csvParser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .get()
                    .parse(reader);

            iterator = csvParser.iterator();
            readCount = 0;
            log.info("book csv reader opened. file={}", file);
        } catch (IOException e) {
            throw new ItemStreamException("Failed to open book csv file: " + file, e);
        }
    }

    @Override
    public BookRawData read() {
        if (iterator == null || !iterator.hasNext()) {
            return null;
        }

        CSVRecord record = iterator.next();
        readCount++;
        if (readCount % 10_000 == 0) {
            log.info("book csv reader progress. file={}, readCount={}", file, readCount);
        }

        BookRawData book = new BookRawData();

        book.setId(parseLong(record.get(CSV_HEADER_SEQ)));
        book.setIsbn(record.get(CSV_HEADER_ISBN));
        book.setTitle(record.get(CSV_HEADER_TITLE));
        book.setAuthorName(record.get(CSV_HEADER_AUTHOR));
        book.setPublisherName(record.get(CSV_HEADER_PUBLISHER));
        book.setFirstPublishDate(parseDate(record.get(CSV_HEADER_PUB_DATE)));
        book.setPrice(parseBigDecimal(record.get(CSV_HEADER_PRICE)));
        book.setImageUrl(record.get(CSV_HEADER_IMAGE));
        book.setBookContent(record.get(CSV_HEADER_CONTENT));
        book.setCategory(record.get(CSV_HEADER_CATEGORY));
        book.setSubtitle(record.get(CSV_HEADER_SUBTITLE));
        book.setEditionPublishDate(parseDate(record.get(CSV_HEADER_EDITION_DATE)));

        return book;
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (csvParser != null) {
                csvParser.close();
            }
            if (reader != null) {
                reader.close();
            }
            log.info("book csv reader closed. file={}, readCount={}", file, readCount);
        } catch (IOException e) {
            throw new ItemStreamException("Failed to close book csv file: " + file, e);
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try{
            return Long.parseLong(value);
        }catch (NumberFormatException e){
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
