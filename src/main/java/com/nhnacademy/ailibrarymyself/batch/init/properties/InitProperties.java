package com.nhnacademy.ailibrarymyself.batch.init.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "init")
public class InitProperties {
    private String bookFile;
    private boolean enable;
    private int batchSize;
    private boolean resetBeforeLoad;
}
