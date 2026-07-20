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
@ConfigurationProperties(prefix = "app.password-reset")
@Validated
@Getter
@Setter
@Slf4j
public class PasswordResetProperties {

    /** Frontend URL users are redirected to for password reset (e.g. https://app.example.com/reset-password). */
    private String baseUrl;

    /** Token lifetime in minutes (default 30). */
    @Min(1)
    private int expirationMinutes = 30;

    @PostConstruct
    void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("app.password-reset.base-url is not set — password reset emails will have broken links");
        }
        if (expirationMinutes < 1) {
            expirationMinutes = 30;
        }
        log.info("Password reset config: baseUrl={}, expirationMinutes={}", baseUrl, expirationMinutes);
    }
}
