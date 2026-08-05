package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quota.StorageQuota;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra logic lõi của StorageQuotaService (backend là nơi duy nhất tính storage):
 *   - limit từ subscription.maxStorageGb (snapshot, không đọc plan)
 *   - used từ account.usedStorageBytes
 *   - overQuota = used > limit
 *   - subtract không bao giờ âm
 */
@SpringBootTest
class StorageQuotaServiceImplTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentPlanRepository paymentPlanRepository;
    @Autowired private StorageQuotaService storageQuotaService;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID accountId;
    private Subscription sub;

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(Account.builder()
                .username("quota_test_" + UUID.randomUUID().toString().substring(0, 8))
                .email("quota_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .passwordHash("x")
                .usedStorageBytes(500L) // 500 bytes đã dùng
                .build());
        accountId = account.getId();

        PaymentPlan free = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> paymentPlanRepository.save(PaymentPlan.builder()
                        .name("FREE")
                        .price(0L)
                        .storageGb(1.0)
                        .isActive(true)
                        .durationDays(-1)
                        .build()));

        sub = subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(free)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(null)
                .pricePaid(0L)
                .storageGbGranted(1.0)
                .aiQuestionsGranted(0)
                .flashcardLimitGranted(0)
                .questionLimitGranted(0)
                .summaryLimitGranted(0)
                .chatLimitGranted(0)
                .tierGranted(0)
                .maxStorageGb(1.0) // snapshot
                .build());
    }

    @AfterEach
    void cleanup() {
        // Không @Transactional class-level → dọn tay giữa các test
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByAccountIdOrderByCreatedAtDesc(accountId));
        accountRepository.deleteById(accountId);
    }

    @Test
    void getQuota_calculatesLimitFromMaxStorageGb() {
        StorageQuota q = storageQuotaService.getQuota(accountId);
        assertEquals(500L, q.storageUsedBytes());
        assertEquals(1024L * 1024 * 1024, q.storageLimitBytes());
        assertEquals(1024L * 1024 * 1024 - 500, q.storageRemainingBytes());
        assertFalse(q.overQuota());
    }

    @Test
    void reserveStorage_rejectsWhenOverQuota() {
        assertDoesNotThrow(() -> storageQuotaService.reserveStorage(accountId, 100L));
        // reserve thành công → used đã tăng
        assertEquals(500L + 100, storageQuotaService.getQuota(accountId).storageUsedBytes());

        assertThrows(RuntimeException.class,
                () -> storageQuotaService.reserveStorage(accountId, 2L * 1024 * 1024 * 1024));
    }

    @Test
    void reserveStorage_incrementsUsedImmediately() {
        storageQuotaService.reserveStorage(accountId, 1024L);
        assertEquals(500L + 1024, storageQuotaService.getQuota(accountId).storageUsedBytes());
    }

    @Test
    void reserveStorage_rollsBack_withOuterTransaction() {
        // Mô phỏng upload flow: reserve + lỗi trong CÙNG tx → rollback trả used.
        // reserveStorage (REQUIRED) join tx của TransactionTemplate.
        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            storageQuotaService.reserveStorage(accountId, 10_000L);
            throw new RuntimeException("simulated upload failure");
        }));
        // Tx rollback → used không đổi
        assertEquals(500L, storageQuotaService.getQuota(accountId).storageUsedBytes());
    }

    @Test
    void concurrentReserves_neverExceedQuota() throws Exception {
        // Setup: limit = 1GB, used = 500 bytes. 4 thread đồng thời reserve 300MB mỗi thread
        // (tổng 1.2GB > limit) → đúng 3 thread thành công (900MB < 1GB), thread 4 phải fail.
        final long MB = 1024L * 1024L;
        int threads = 4;
        long perThread = 300L * MB;
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();

        List<Thread> workers = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    transactionTemplate.executeWithoutResult(status -> {
                        storageQuotaService.reserveStorage(accountId, perThread);
                        success.incrementAndGet();
                    });
                } catch (RuntimeException ignored) {
                    // over quota — expected cho thread thừa
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            workers.add(t);
            t.start();
        }

        ready.await();
        go.countDown();
        for (Thread w : workers) w.join(30_000);

        long finalUsed = storageQuotaService.getQuota(accountId).storageUsedBytes();
        // Không bao giờ vượt: used tối đa = limit
        assertTrue(finalUsed <= 1024L * 1024L * 1024L,
                "Used " + finalUsed + " exceeded 1GB limit");
        // Serialize đúng: 3 thread thành công = 500 + 3*300MB
        assertEquals(500L + 3L * perThread, finalUsed);
        assertEquals(3, success.get());
    }

    @Test
    void reserveStorage_multiFileTotal() {
        // Upload nhiều file 1 request: reserve bằng tổng, không phải per-file
        storageQuotaService.reserveStorage(accountId, 10_000L + 20_000L);
        assertEquals(500L + 30_000L, storageQuotaService.getQuota(accountId).storageUsedBytes());
    }

    @Test
    void subtractUsedBytes_neverNegative() {
        storageQuotaService.subtractUsedBytes(accountId, 100_000L); // > 500 đã dùng
        assertEquals(0L, storageQuotaService.getQuota(accountId).storageUsedBytes());
    }

    @Test
    void overQuota_trueWhenUsedExceedsLimit() {
        // Giảm maxStorageGb xuống 0 — over quota ngay
        sub.setMaxStorageGb(0.0);
        subscriptionRepository.save(sub);
        assertTrue(storageQuotaService.getQuota(accountId).overQuota());
    }

    @Test
    void expiredSubscription_violatesInvariant_throws() {
        sub.setEndDate(Instant.now().minus(1, ChronoUnit.DAYS));
        subscriptionRepository.save(sub);
        // Invariant: không fallback FREE — không có ACTIVE hợp lệ → IllegalStateException
        assertThrows(IllegalStateException.class,
                () -> storageQuotaService.getQuota(accountId));
    }
}
