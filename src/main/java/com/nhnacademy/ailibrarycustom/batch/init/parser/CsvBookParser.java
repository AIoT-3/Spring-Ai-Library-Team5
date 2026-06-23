package com.nhnacademy.ailibrarycustom.batch.init.parser;

import com.nhnacademy.ailibrarycustom.batch.init.dto.BookRawData;
import com.nhnacademy.ailibrarycustom.batch.init.event.BookParsedEvent;
import com.nhnacademy.ailibrarycustom.batch.init.event.BookParsingCompleteEvent;
import com.nhnacademy.ailibrarycustom.batch.init.properties.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvBookParser {
    private final ApplicationEventPublisher eventPublisher;
    private final InitProperties initProperties;

    private static final String DATE_FORMAT_YYYYMMDD = "yyyyMMdd";
    private static final String DATE_FORMAT_DASH = "yyyy-MM-dd";

    private static final String CSV_HEADER_SEQ = "SEQ_NO";
    private static final String CSV_HEADER_ISBN = "ISBN_THIRTEEN_NO";
    private static final String CSV_HEADER_CATEGORY = "KDC_NM";
    private static final String CSV_HEADER_TITLE = "TITLE_NM";
    private static final String CSV_HEADER_AUTHOR = "AUTHR_NM";
    private static final String CSV_HEADER_PUBLISHER = "PUBLISHER_NM";
    private static final String CSV_HEADER_PUB_DATE = "PBLICTE_DE";
    private static final String CSV_HEADER_PRICE = "PRC_VALUE";
    private static final String CSV_HEADER_IMAGE = "IMAGE_URL";
    private static final String CSV_HEADER_CONTENT = "BOOK_INTRCN_CN";
    private static final String CSV_HEADER_SUBTITLE = "TITLE_SBST_NM";
    private static final String CSV_HEADER_EDITION_DATE = "TWO_PBLICTE_DE";

    public void parse() throws IOException {
        String filepath = initProperties.getBookFile();
        log.info("CSV 파싱 시작 : {} ", filepath);

        ClassPathResource resource = new ClassPathResource(filepath);

        try( Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)
        ){
            int count = 0;

            for(CSVRecord record : parser){
                BookRawData book = mapToBookRawData(record);
                eventPublisher.publishEvent(new BookParsedEvent(book));

                count++;
                if(count % 1000 == 0){
                    log.info("{}권 파싱 완료", count);
                }
            }
            log.info("총 {}권 파싱 완료", count);

            eventPublisher.publishEvent(new BookParsingCompleteEvent());
        }
    }

    private BookRawData mapToBookRawData(CSVRecord record){
        BookRawData book = new BookRawData();

        String seq;
        try {
            seq = record.get(CSV_HEADER_SEQ);
        }catch (IllegalArgumentException e){
            seq = record.get("\uFEFF" + CSV_HEADER_SEQ);
        }
        if(seq != null && !seq.isBlank()){
            book.setId(Long.parseLong(seq));
        }

        book.setIsbn(record.get(CSV_HEADER_ISBN));
        book.setCategory(record.get(CSV_HEADER_CATEGORY));
        book.setTitle(record.get(CSV_HEADER_TITLE));
        book.setAuthorName(record.get(CSV_HEADER_AUTHOR));
        book.setPublisherName(record.get(CSV_HEADER_PUBLISHER));

        String pbDate = record.get(CSV_HEADER_PUB_DATE);
        if(pbDate != null && !pbDate.isBlank()){
           book.setFirstPublishDate(convertToLocalDate(pbDate));
        }

        String editionDate = record.get(CSV_HEADER_EDITION_DATE);
        if(editionDate != null && !editionDate.isBlank()){
            book.setEditionPublishDate(convertToLocalDate(editionDate));
        }

        String price = record.get(CSV_HEADER_PRICE);
        if(price != null && !price.isBlank()){
            book.setPrice(new BigDecimal(price));
        }

        book.setImageUrl(record.get(CSV_HEADER_IMAGE));
        book.setBookContent(record.get(CSV_HEADER_CONTENT));
        book.setSubtitle(record.get(CSV_HEADER_SUBTITLE));

        log.debug("Parsed book: {}", book);

        return book;
    }

    private LocalDate convertToLocalDate(String strDate){
        if(strDate == null || strDate.isBlank()){
            return null;
        }
        strDate = strDate.replaceAll("[^0-9]", "");

        if(strDate.isEmpty()){
            return null;
        }

        try{
            return LocalDate.parse(strDate, DateTimeFormatter.ofPattern(DATE_FORMAT_YYYYMMDD));

        }catch (Exception e){
            log.info("날짜 파싱 실패 {}", e);
            return null;
        }
    }

}
