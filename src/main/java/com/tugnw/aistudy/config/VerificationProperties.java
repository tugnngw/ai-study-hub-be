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
@ConfigurationProperties(prefix = "app.verification")
@Validated
@Getter
@Setter
@Slf4j
public class VerificationProperties {

    /** Frontend URL users are redirected to for verification (e.g. https://app.example.com/verify-email). */
    private String baseUrl;

    /** Token lifetime in hours (default 24). */
    @Min(1)
    private int expirationHours = 24;

    /** Whether to auto-send verification email on registration (default true). */
    private boolean autoSendOnRegister = true;

    /** Whether to enable periodic cleanup of unverified accounts (default true). */
    private boolean cleanupEnabled = true;

    /** Retention period in hours before an unverified LOCAL account is deleted (default 48). */
    @Min(1)
    private int cleanupHours = 48;

    @PostConstruct
    void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("app.verification.base-url is not set — verification emails will have broken links");
        }
        if (expirationHours < 1) {
            expirationHours = 24;
        }
        if (cleanupHours < 1) {
            cleanupHours = 48;
        }
        log.info("Verification config: baseUrl={}, expirationHours={}, autoSendOnRegister={}, cleanupEnabled={}, cleanupHours={}",
                baseUrl, expirationHours, autoSendOnRegister, cleanupEnabled, cleanupHours);
    }
}
