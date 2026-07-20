package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.config.VerificationProperties;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCleanupService {

    private final AccountRepository accountRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationProperties verificationProperties;

    /**
     * Soft-delete LOCAL accounts that were never verified after the retention period.
     * Runs daily at 03:00 server time.
     *
     * Soft-delete clears email so the unique constraint is released.
     * Verification tokens are deleted manually (DB cascade only fires on hard delete).
     * Subscriptions are preserved (orphaned data harmless, FK is accountId).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnverifiedAccounts() {
        if (!verificationProperties.isCleanupEnabled()) {
            log.debug("Account cleanup is disabled");
            return;
        }

        Instant cutoff = Instant.now().minusSeconds(verificationProperties.getCleanupHours() * 3600L);
        List<UUID> accountIds = accountRepository.findUnverifiedLocalAccountIds(AuthProvider.LOCAL, cutoff);

        if (accountIds.isEmpty()) {
            log.debug("No unverified accounts to clean up");
            return;
        }

        log.info("Soft-deleting {} unverified LOCAL account(s) older than {} hours",
                accountIds.size(), verificationProperties.getCleanupHours());

        // Delete verification tokens first (child rows)
        int tokensDeleted = verificationTokenRepository.deleteByAccountIdIn(accountIds);

        // Soft-delete accounts: clear email, mark deleted
        int accountsUpdated = accountRepository.softDeleteUnverifiedAccounts(accountIds, Instant.now());

        log.info("Cleanup complete: {} token(s) deleted, {} account(s) soft-deleted",
                tokensDeleted, accountsUpdated);
    }
}
