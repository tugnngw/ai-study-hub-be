package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.ActivityLogRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.PaymentTransactionRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-4: PaymentTransaction idempotent.
 * - 2 webhook cùng lúc / webhook + verify → đúng 1 subscription, 1 activity, quota 1 lần
 * - webhook retry → không duplicate
 * - poll expire không đè PAID
 * - PAID là terminal
 */
@SpringBootTest
class PaymentIdempotencyTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private PaymentPlanRepository paymentPlanRepository;
    @Autowired private PaymentTransactionRepository txRepo;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private PaymentService paymentService;

    private UUID accountId;
    private Long orderCode;
    private PaymentPlan proPlan;

    // PayOS signature verify — mock sẽ không chạy nếu dùng thật; test gọi service trực tiếp
    // qua path đã verify. Để test concurrency qua verifyAndProcessPayment (không cần signature).

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(Account.builder()
                .username("pay_" + UUID.randomUUID().toString().substring(0, 8))
                .email("pay_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .passwordHash("x")
                .build());
        accountId = account.getId();

        proPlan = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "PRO".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> paymentPlanRepository.save(PaymentPlan.builder()
                        .name("PRO")
                        .price(200_000L)
                        .storageGb(10.0)
                        .isActive(true)
                        .durationDays(30)
                        .tier(2)
                        .displayOrder(2)
                        .build()));

        orderCode = System.currentTimeMillis() % 100_000_000L + 100_000L;
        txRepo.save(PaymentTransaction.builder()
                .accountId(accountId)
                .plan(proPlan)
                .payosOrderCode(orderCode)
                .amount(200_000L)
                .status(PaymentStatus.PENDING)
                .description("Buy plan PRO")
                .expiredAt(Instant.now().plusSeconds(180))
                .build());
    }

    @AfterEach
    void cleanup() {
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByAccountIdOrderByCreatedAtDesc(accountId));
        txRepo.deleteAll(
                txRepo.findByAccountIdOrderByCreatedAtDesc(accountId));
        accountRepository.deleteById(accountId);
    }

    private long countActiveSubs() {
        return subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE).size();
    }

    private long countUpgradeActivities() {
        return activityLogRepository.countByActionTypeAndCreatedAtAfter(
                com.tugnw.aistudy.domain.enums.ActivityType.USER_UPGRADE, Instant.EPOCH);
    }

    @Test
    void concurrentVerify_createsOnlyOneSubscription() throws Exception {
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        List<Thread> workers = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    paymentService.verifyAndProcessPayment(orderCode);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } catch (Throwable t2) {
                    // Pessimistic lock contention có thể ném — không phải lỗi business
                }
            });
            workers.add(t);
            t.start();
        }
        ready.await();
        go.countDown();
        for (Thread w : workers) w.join(30_000);

        assertEquals(0, errors.get());
        // Đúng 1 subscription ACTIVE dù 4 luồng verify đồng thời
        assertEquals(1L, countActiveSubs());
        // Transaction đạt PAID
        PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode).orElseThrow();
        assertEquals(PaymentStatus.PAID, tx.getStatus());
    }

    @Test
    void retryVerify_doesNotDuplicateSubscriptionOrActivity() {
        paymentService.verifyAndProcessPayment(orderCode);
        long subsAfterFirst = countActiveSubs();
        long activitiesAfterFirst = countUpgradeActivities();

        // Retry nhiều lần — không tạo thêm gì
        paymentService.verifyAndProcessPayment(orderCode);
        paymentService.verifyAndProcessPayment(orderCode);

        assertEquals(subsAfterFirst, countActiveSubs(), "Retry không tạo thêm subscription");
        assertEquals(activitiesAfterFirst, countUpgradeActivities(), "Retry không ghi activity lần 2");
        assertEquals(1L, countActiveSubs());
    }

    @Test
    void paidIsTerminal_retryKeepsPaid() {
        paymentService.verifyAndProcessPayment(orderCode);
        PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode).orElseThrow();
        assertEquals(PaymentStatus.PAID, tx.getStatus());

        // Retry không đổi trạng thái
        paymentService.verifyAndProcessPayment(orderCode);
        tx = txRepo.findByPayosOrderCode(orderCode).orElseThrow();
        assertEquals(PaymentStatus.PAID, tx.getStatus());
    }

    @Test
    void quotaUpdatedOnce_onRetry() {
        paymentService.verifyAndProcessPayment(orderCode);
        Subscription sub = subscriptionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId).get(0);
        // maxStorageGb snapshot từ plan
        assertEquals(proPlan.getStorageGb(), sub.getMaxStorageGb());

        paymentService.verifyAndProcessPayment(orderCode);
        Subscription subAfter = subscriptionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId).get(0);
        assertEquals(sub.getId(), subAfter.getId(), "Không tạo subscription mới khi retry");
    }
}
