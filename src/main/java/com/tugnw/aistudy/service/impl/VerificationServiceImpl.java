package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.config.VerificationProperties;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.VerificationToken;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.exception.TokenAlreadyUsedException;
import com.tugnw.aistudy.exception.TokenExpiredException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.VerificationTokenRepository;
import com.tugnw.aistudy.service.EmailService;
import com.tugnw.aistudy.service.VerificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final VerificationProperties verificationProperties;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void sendVerificationEmail(String email) {
        // Silently return if no account — prevents user enumeration
        Optional<Account> opt = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (opt.isEmpty()) {
            log.info("Send-verification requested for unknown email (suppressed)");
            return;
        }
        Account account = opt.get();
        if (account.isEmailVerified()) {
            log.info("Account {} already verified, skipping send", account.getId());
            return;
        }

        doSendVerification(account);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        Account account = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidTokenException("Account not found with email: " + email));

        if (account.isEmailVerified()) {
            log.info("Account {} already verified, skipping resend", account.getId());
            return;
        }

        doSendVerification(account);
    }

    @Override
    @Transactional
    public void resendVerificationByUsername(String username) {
        Account account = accountRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(username);
        if (account == null || account.getEmail() == null || account.isEmailVerified()) {
            log.info("Resend-by-username: no eligible account for user '{}' (suppressed)", username);
            return;
        }
        doSendVerification(account);
    }

    @Override
    @Transactional
    public void verify(String token) {
        VerificationToken vt = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (vt.isExpired()) {
            throw new TokenExpiredException("Verification token has expired. Please request a new one.");
        }

        if (vt.isVerified()) {
            throw new TokenAlreadyUsedException("This verification link has already been used.");
        }

        // Lock the account row: serializes against concurrent resend/verify.
        // After this call the entity reflects the latest committed DB state
        // (e.g. emailVerified=true if another verify just committed).
        Account account = vt.getAccount();
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        if (account.isEmailVerified()) {
            // Another transaction already verified this account.
            // Mark this token as used to clean it up.
            vt.setVerifiedAt(Instant.now());
            verificationTokenRepository.save(vt);
            int remaining = verificationTokenRepository.deleteByAccount(account);
            log.info("Verification completed; removed {} remaining token(s)", remaining);
            return;
        }

        // Mark account as verified
        account.setEmailVerified(true);
        accountRepository.save(account);

        // Mark token as used
        vt.setVerifiedAt(Instant.now());
        verificationTokenRepository.save(vt);

        // Clean up any leftover tokens for this account
        int remaining = verificationTokenRepository.deleteByAccount(account);
        log.info("Account {} email verified successfully", account.getId());
        log.info("Verification completed; removed {} remaining token(s)", remaining);
    }

    /**
     * Reuse or generate a verification token, then send the email.
     *
     * Thread safety: acquires a pessimistic DB row lock on the Account
     * at the start, serializing all concurrent send/verify operations
     * for this account.  After the lock, re-checks isEmailVerified in
     * case a concurrent verify() just committed.
     *
     * Token lifecycle:
     * - If an unverified, unexpired token exists → reuse it (same link).
     * - If no active token → clean stale/verified rows, create fresh one.
     * - At most one active token exists at a time.
     */
    private void doSendVerification(Account account) {
        // Acquire row-level lock.  Blocks until any preceding concurrent
        // transaction for this account commits.  After this call the
        // entity reflects the latest committed state — preventing the
        // verify-vs-resend race described in the audit.
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        // Double-check: a concurrent verify() might have just committed.
        if (account.isEmailVerified()) {
            log.info("Account {} already verified, skipping send", account.getId());
            return;
        }

        // ── Reuse existing active token ────────────────────────────────────
        Optional<VerificationToken> existing = verificationTokenRepository
                .findActiveByAccount(account, Instant.now());

        if (existing.isPresent()) {
            VerificationToken vt = existing.get();
            log.info("Reusing existing verification token for account {}", account.getId());
            queueEmail(account, vt.getToken());
            return;
        }

        // ── No active token — clean slate, create fresh one ────────────────
        verificationTokenRepository.deleteByAccount(account);
        verificationTokenRepository.flush();

        String tokenValue = UUID.randomUUID().toString();

        VerificationToken vt = VerificationToken.builder()
                .token(tokenValue)
                .account(account)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(verificationProperties.getExpirationHours() * 3600L))
                .build();
        verificationTokenRepository.save(vt);

        log.info("Generated new verification token for account {}", account.getId());
        queueEmail(account, tokenValue);
    }

    /**
     * Build the verification URL and dispatch the email via the async mail
     * executor.  Logged as "queued" rather than "sent" because the async
     * delivery may still fail.
     */
    private void queueEmail(Account account, String token) {
        String baseUrl = verificationProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("app.verification.base-url not configured — cannot send verification email");
            return;
        }

        String verifyLink = baseUrl + "?token=" + token;

        Map<String, Object> context = Map.of(
                "title", "Verify your email",
                "name", account.getFullName(),
                "buttonUrl", verifyLink,
                "buttonText", "Verify Email",
                "expirationHours", verificationProperties.getExpirationHours()
        );

        emailService.sendHtmlEmail(
                account.getEmail(),
                "Verify your email",
                "email/verify-email",
                context
        );

        log.info("Verification email queued for delivery to account {} with token {}", account.getId(), token);
    }
}
