package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.repository.PasswordResetTokenRepository;
import com.tugnw.aistudy.repository.VerificationTokenRepository;
import com.tugnw.aistudy.service.TokenCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupServiceImpl implements TokenCleanupService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // daily at 03:00
    @Transactional
    public void cleanExpiredVerificationTokens() {
        int deleted = verificationTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired verification tokens", deleted);
        }
    }

    @Override
    @Scheduled(cron = "0 0 * * * ?") // hourly
    @Transactional
    public void cleanExpiredPasswordResetTokens() {
        int deleted = passwordResetTokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired password reset tokens", deleted);
        }
    }
}
