package com.nhnacademy.ailibraryteam5;

import com.nhnacademy.ailibraryteam5.core.book.rag.cache.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class AiLibraryTeam5Application {

    public static void main(String[] args) {
        SpringApplication.run(AiLibraryTeam5Application.class, args);
    }

}
