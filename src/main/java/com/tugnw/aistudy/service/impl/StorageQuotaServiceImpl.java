package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.quota.StorageQuota;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.StorageQuotaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageQuotaServiceImpl implements StorageQuotaService {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final AccountRepository accountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntityManager entityManager;

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    /**
     * Đọc subscription ACTIVE hợp lệ (mới nhất). KHÔNG fallback FREE — invariant
     * "account luôn có đúng 1 ACTIVE subscription" do SubscriptionService đảm bảo.
     * Không có subscription → IllegalStateException (không bao giờ xảy ra nếu invariant đúng).
     */
    private Subscription resolveActiveSubscription(UUID accountId) {
        Instant now = Instant.now();
        return subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE).stream()
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(now))
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElseThrow(() -> new IllegalStateException(
                        "Account " + accountId + " has no active subscription (invariant violated)"));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public StorageQuota getQuota(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        long used = account.getUsedStorageBytes() != null ? account.getUsedStorageBytes() : 0L;
        Subscription sub = resolveActiveSubscription(accountId);
        long limitBytes = toBytes(sub.getMaxStorageGb());
        long remaining = Math.max(0L, limitBytes - used);
        return new StorageQuota(used, limitBytes, remaining, used > limitBytes);
    }

    /**
     * Reserve: lock Account từ SELECT → đọc used → kiểm tra limit → cập nhật used NGAY.
     * Quyết định + ghi nằm cùng transaction upload (DocumentServiceImpl) — atomic theo account.
     */
    @Override
    @Transactional
    public void reserveStorage(UUID accountId, long incomingBytes) {
        Account account = accountRepository.findAccountForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        long limitBytes = toBytes(resolveActiveSubscription(accountId).getMaxStorageGb());
        long used = account.getUsedStorageBytes() != null ? account.getUsedStorageBytes() : 0L;
        if (used + incomingBytes > limitBytes) {
            throw new RuntimeException(
                "Bạn đã sử dụng hết dung lượng lưu trữ (" + formatBytes(used) + "/" + formatBytes(limitBytes) + "). "
                + "Vui lòng nâng cấp gói Premium để có thêm không gian."
            );
        }
        account.setUsedStorageBytes(used + incomingBytes);
        accountRepository.save(account);
    }

    private static long toBytes(Double maxStorageGb) {
        if (maxStorageGb == null) return BYTES_PER_GB;
        double bytes = maxStorageGb * BYTES_PER_GB;
        if (bytes < 0) return Long.MAX_VALUE; // -1 = unlimited
        return (long) bytes;
    }

    @Override
    @Transactional
    public void subtractUsedBytes(UUID accountId, long bytes) {
        if (bytes <= 0) return;
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);
        long current = account.getUsedStorageBytes() != null ? account.getUsedStorageBytes() : 0L;
        account.setUsedStorageBytes(Math.max(0L, current - bytes)); // không bao giờ âm
        accountRepository.save(account);
    }
}
