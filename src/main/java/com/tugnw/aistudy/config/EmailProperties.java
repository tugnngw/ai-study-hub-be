package com.tugnw.aistudy.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.mail")
@Validated
@Getter
@Setter
@Slf4j
public class EmailProperties {

    /** Sender "from" address shown in outgoing emails. */
    private String from;

    /** Master switch — set false to skip all email sending without errors. */
    private boolean enabled = true;

    /** Max retry attempts when sending fails (default 3). */
    @Min(1)
    private int retryMaxAttempts = 3;

    /** Delay in ms between retries (default 1000). */
    @Min(100)
    private long retryDelayMs = 1000L;

    @PostConstruct
    void validate() {
        if (enabled && (from == null || from.isBlank())) {
            log.warn("app.mail.from is not set — email sending will fail at runtime if enabled");
        }
        if (enabled && retryMaxAttempts < 1) {
            retryMaxAttempts = 1;
        }
        log.info("Mail config: enabled={}, from={}, retryMaxAttempts={}", enabled, from, retryMaxAttempts);
    }
}
