package com.nhnacademy.library.batch.init.parser;

import com.nhnacademy.library.batch.init.dto.BookRawData;
import com.nhnacademy.library.batch.init.event.BookParsedEvent;
import com.nhnacademy.library.batch.init.event.BookParsingCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class CsvBookParser {
    private final ApplicationEventPublisher eventPublisher;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ofPattern("[yyyy-MM-dd][yyyyMMdd][yyyy]"))
            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter();

    public void parse(String filePath) throws IOException{
        log.info("CSV 파싱 시작: {}", filePath);

        ClassPathResource resource = new ClassPathResource(filePath);

        try(InputStreamReader reader = new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8);
            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .build().parse(reader)){

            log.info("인식된 헤더 목록: {}", parser.getHeaderNames());

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

        try{
            book.setId(Long.valueOf(record.get(0).trim()));
        }catch (IllegalArgumentException e){
            book.setId(null);
        }

        book.setIsbn(record.get("ISBN_THIRTEEN_NO"));
        book.setVolumeTitle(record.get("VLM_NM"));
        book.setTitle(record.get("TITLE_NM"));
        book.setAuthorName(record.get("AUTHR_NM"));
        book.setPublisherName(record.get("PUBLISHER_NM"));
        book.setFirstPublishDate(convertToLocalDate(record.get("PBLICTE_DE")));
        book.setPrice(convertToBigDecimal(record.get("PRC_VALUE")));
        book.setImageUrl(record.get("IMAGE_URL"));
        book.setBookContent(record.get("BOOK_INTRCN_CN"));
        book.setSubtitle(record.get("TITLE_SBST_NM"));
        book.setEditionPublishDate(convertToLocalDate(record.get("TWO_PBLICTE_DE")));

        return book;
    }

    private LocalDate convertToLocalDate(String dateStr){
        if(dateStr == null || dateStr.isBlank()){
            return null;
        }

        try{
            String cleanStr = dateStr.trim().replaceAll("\\s+", "");
            return LocalDate.parse(dateStr.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e){
            log.warn("잘못된 날짜 형식 데이터 건너뜀: {}", dateStr);
            return null;
        }
    }

    private BigDecimal convertToBigDecimal(String numStr){
        if (numStr == null || numStr.isBlank()){
            return null;
        }

        try{
            String cleanStr = numStr.trim()
                    .replace(",", "")
                    .replaceAll("\\s+", "");

            return new BigDecimal(cleanStr);
        }catch (NumberFormatException e){
            log.warn("숫자로 변환할 수 없는 데이터: {}", numStr);
            return null;
        }
    }
}
