package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.AccountToken;
import com.tugnw.aistudy.domain.enums.AccountTokenType;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.exception.TokenAlreadyUsedException;
import com.tugnw.aistudy.exception.TokenExpiredException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.AccountTokenRepository;
import com.tugnw.aistudy.service.EmailService;
import com.tugnw.aistudy.service.PasswordResetService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long OTP_EXPIRY_MINUTES = 10;

    @Override
    @Transactional
    public void requestReset(String email) {
        Optional<Account> opt = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (opt.isEmpty()) {
            log.info("Password-reset requested for unknown email (suppressed)");
            return;
        }

        Account account = opt.get();

        if (!account.isEmailVerified() || account.getEmail() == null) {
            log.info("Password-reset skipped for unverified account {}", account.getId());
            return;
        }

        doSendOtp(account);
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp) {
        Account account = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidTokenException("Invalid request"));

        // Lock the token row to observe latest committed state
        validateToken(account, otp);
    }

    @Override
    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        Account account = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidTokenException("Invalid request"));

        // Load and lock the token row with PESSIMISTIC_WRITE.
        // The lock is acquired BEFORE any validation, so two concurrent
        // reset requests using the same token serialize on this query:
        //   Tx1 acquires lock → Tx2 blocks at this query
        //   Tx1 validates → resets password → marks token used → commits
        //   Tx2 acquires lock → reads fresh row (usedAt != null) → fails
        AccountToken token = accountTokenRepository
                .findByTokenAndTypeWithWriteLock(otp, AccountTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification code"));

        // Ensure token belongs to this account
        if (!token.getAccount().getId().equals(account.getId())) {
            throw new InvalidTokenException("Invalid verification code");
        }

        if (token.isExpired()) {
            throw new TokenExpiredException("Verification code has expired. Please request a new one.");
        }

        if (token.isUsed()) {
            throw new TokenAlreadyUsedException("This verification code has already been used.");
        }

        // Update password
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        // Consume this token and remove all PASSWORD_RESET tokens for the account
        accountTokenRepository.deleteByAccountAndType(account, AccountTokenType.PASSWORD_RESET);

        log.info("Password reset successfully for account {}", account.getId());
    }

    private AccountToken validateToken(Account account, String otp) {
        // PESSIMISTIC_READ — ensures we see latest committed state.
        // Blocks if a concurrent resetPassword holds PESSIMISTIC_WRITE on this row.
        // Does NOT block other readers (other verifyOtp calls).
        AccountToken token = accountTokenRepository
                .findByTokenAndTypeWithReadLock(otp, AccountTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification code"));

        if (!token.getAccount().getId().equals(account.getId())) {
            throw new InvalidTokenException("Invalid verification code");
        }

        if (token.isExpired()) {
            throw new TokenExpiredException("Verification code has expired. Please request a new one.");
        }

        if (token.isUsed()) {
            throw new TokenAlreadyUsedException("This verification code has already been used.");
        }

        return token;
    }

    private void doSendOtp(Account account) {
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        Optional<AccountToken> existing = accountTokenRepository
                .findActiveByAccountAndType(account, AccountTokenType.PASSWORD_RESET, Instant.now());

        if (existing.isPresent()) {
            AccountToken vt = existing.get();
            log.info("Reusing existing PASSWORD_RESET token for account {}", account.getId());
            emailService.sendPasswordResetEmail(account.getEmail(), account.getFullName(), vt.getToken());
            return;
        }

        accountTokenRepository.deleteByAccountAndType(account, AccountTokenType.PASSWORD_RESET);

        String otp = generateOtp();

        AccountToken token = AccountToken.builder()
                .token(otp)
                .account(account)
                .type(AccountTokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60))
                .build();
        accountTokenRepository.save(token);

        log.info("Generated new PASSWORD_RESET OTP for account {}", account.getId());
        emailService.sendPasswordResetEmail(account.getEmail(), account.getFullName(), otp);
    }

    /**
     * Generate a secure 6-digit numeric OTP in range 100000–999999.
     */
    private String generateOtp() {
        return String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
    }
}
