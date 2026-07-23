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
import com.tugnw.aistudy.service.VerificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    private final AccountRepository accountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final EmailService emailService;
    private final EntityManager entityManager;

    /** Token lifetime in hours. */
    private static final long EXPIRATION_HOURS = 24;

    @Override
    @Transactional
    public void sendVerificationEmail(String email) {
        Optional<Account> opt = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (opt.isEmpty()) return;

        Account account = opt.get();

        if (account.isEmailVerified()) return;

        doSendVerification(account);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Account account = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidTokenException("Account not found with email: " + email));

        if (account.isEmailVerified()) return;

        doSendVerification(account);
    }

    @Override
    @Transactional
    public void resendVerificationByUsername(String username) {
        Account account = accountRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(username);
        if (account == null || account.getEmail() == null || account.isEmailVerified()) return;
        doSendVerification(account);
    }

    @Override
    @Transactional
    public void verify(String token) {
        AccountToken vt = accountTokenRepository.findByTokenAndType(token, AccountTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (vt.isExpired())
            throw new TokenExpiredException("Verification token has expired. Please request a new one.");

        if (vt.isUsed())
            throw new TokenAlreadyUsedException("This verification link has already been used.");

        Account account = vt.getAccount();

        // Lock the account row — serialize concurrent verify/resend
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        // Re-check after lock (a concurrent verify may have committed first)
        if (account.isEmailVerified()) {
            vt.setUsedAt(Instant.now());
            accountTokenRepository.save(vt);
            return;
        }

        // Mark account as verified
        account.setEmailVerified(true);
        accountRepository.save(account);

        // Mark token as used
        vt.setUsedAt(Instant.now());
        accountTokenRepository.save(vt);
    }

    // ============ HELPER METHODS ============

    /**
     * Reuse or generate an email verification token.
     *
     * Thread safety: acquires a pessimistic DB row lock on the Account,
     * serializing all concurrent send/verify operations for this account.
     */
    private void doSendVerification(Account account) {
        // Serialize on the account row
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        // Re-check after lock: a concurrent verify may have just committed
        if (account.isEmailVerified()) return;

        // Reuse existing active token — enforce 60s cooldown to prevent SMTP spam
        Optional<AccountToken> existing = accountTokenRepository
                .findActiveByAccountAndType(account, AccountTokenType.EMAIL_VERIFICATION, Instant.now());

        if (existing.isPresent()) {
            AccountToken vt = existing.get();
            Instant cooldownEnd = vt.getCreatedAt().plusSeconds(60);
            if (Instant.now().isBefore(cooldownEnd)) {
                long secondsLeft = java.time.Duration.between(Instant.now(), cooldownEnd).toSeconds();
                throw new IllegalArgumentException(
                    "Vui lòng đợi " + Math.max(1, secondsLeft) + " giây trước khi yêu cầu gửi lại email xác thực.");
            }
            emailService.sendVerificationEmail(account.getEmail(), account.getFullName(), vt.getToken());
            return;
        }

        // No active token — clean stale tokens, create fresh one
        accountTokenRepository.deleteByAccountAndType(account, AccountTokenType.EMAIL_VERIFICATION);

        String tokenValue = UUID.randomUUID().toString();

        AccountToken vt = AccountToken.builder()
                .token(tokenValue)
                .account(account)
                .type(AccountTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(EXPIRATION_HOURS * 3600))
                .build();
        accountTokenRepository.save(vt);

        emailService.sendVerificationEmail(account.getEmail(), account.getFullName(), tokenValue);
    }
}
