package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.config.PasswordResetProperties;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PasswordResetToken;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.exception.PasswordMismatchException;
import com.tugnw.aistudy.exception.PasswordReuseException;
import com.tugnw.aistudy.exception.TokenAlreadyUsedException;
import com.tugnw.aistudy.exception.TokenExpiredException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PasswordResetTokenRepository;
import com.tugnw.aistudy.service.EmailService;
import com.tugnw.aistudy.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties passwordResetProperties;

    @Override
    @Transactional
    public void requestReset(String email) {
        // Silently return if no account — prevents user enumeration
        Optional<Account> opt = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (opt.isEmpty()) {
            log.info("Password reset requested for unknown email (suppressed)");
            return;
        }

        Account account = opt.get();
        if (account.getEmail() == null || !account.isEmailVerified()) {
            log.info("Password reset suppressed for account {}: email null or unverified", account.getId());
            return;
        }

        doSendResetToken(account);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (prt.isExpired()) {
            throw new TokenExpiredException("Password reset token has expired. Please request a new one.");
        }

        if (prt.isUsed()) {
            throw new TokenAlreadyUsedException("This password reset link has already been used.");
        }

        Account account = prt.getAccount();

        // Reject if same as current password
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new PasswordReuseException("New password must be different from the current password.");
        }

        // Update password
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        // Note: Stateless JWT refresh tokens cannot be revoked here.
        // Already-issued refresh JWTs remain valid until their natural expiry (7 days).
        // A future enhancement could introduce a blocklist or short-lived rotation.

        // Invalidate any remaining active reset tokens
        passwordResetTokenRepository.deleteByAccount(account);

        // Mark this token as used
        prt.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(prt);

        log.info("Password reset successful for account {}", account.getId());
    }

    private void doSendResetToken(Account account) {
        // Remove all existing tokens for this account (clean slate)
        passwordResetTokenRepository.deleteByAccount(account);
        passwordResetTokenRepository.flush();

        // Create new token
        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken prt = PasswordResetToken.builder()
                .token(tokenValue)
                .account(account)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(passwordResetProperties.getExpirationMinutes() * 60L))
                .build();
        passwordResetTokenRepository.save(prt);

        // Build reset URL — points to frontend
        String baseUrl = passwordResetProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("app.password-reset.base-url not configured — cannot send password reset email");
            return;
        }
        String resetLink = baseUrl + "?token=" + tokenValue;

        Map<String, Object> context = Map.of(
                "title", "Reset your password",
                "name", account.getFullName(),
                "buttonUrl", resetLink,
                "buttonText", "Reset Password",
                "expirationMinutes", passwordResetProperties.getExpirationMinutes()
        );

        emailService.sendHtmlEmail(
                account.getEmail(),
                "Reset your password",
                "email/reset-password",
                context
        );

        log.info("Password reset email sent to account {}", account.getId());
    }
}
