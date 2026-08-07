package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra invariant "account luôn có đúng 1 subscription ACTIVE chưa hết hạn":
 *   - ensure tạo FREE khi không có subscription
 *   - idempotent khi đã hợp lệ
 *   - expire + tạo FREE mới khi premium hết hạn
 *   - dedupe nhiều ACTIVE
 */
@SpringBootTest
@Transactional
class SubscriptionLifecycleTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentPlanRepository paymentPlanRepository;
    @Autowired private SubscriptionService subscriptionService;

    private UUID accountId;
    private PaymentPlan freePlan;
    private PaymentPlan proPlan;

    @BeforeEach
    void setup() {
        freePlan = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> paymentPlanRepository.save(PaymentPlan.builder()
                        .name("FREE")
                        .price(0L)
                        .storageGb(1.0)
                        .isActive(true)
                        .durationDays(-1)
                        .build()));
        proPlan = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "PRO".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> paymentPlanRepository.save(PaymentPlan.builder()
                        .name("PRO")
                        .price(100_000L)
                        .storageGb(10.0)
                        .isActive(true)
                        .durationDays(30)
                        .build()));

        Account account = accountRepository.save(Account.builder()
                .username("sub_test_" + UUID.randomUUID().toString().substring(0, 8))
                .email("sub_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .passwordHash("x")
                .build());
        accountId = account.getId();
    }

    private long countActive() {
        return subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE).size();
    }

    @Test
    void ensure_createsFree_whenNoSubscription() {
        assertFalse(subscriptionService.hasActiveSubscription(accountId));
        Subscription s = subscriptionService.ensureActiveSubscription(accountId);
        assertEquals(SubscriptionStatus.ACTIVE, s.getStatus());
        assertEquals("FREE", s.getPlan().getName().toUpperCase());
        assertNull(s.getEndDate()); // FREE không bao giờ hết hạn
        assertEquals(1L, countActive());
        assertTrue(subscriptionService.hasActiveSubscription(accountId));
    }

    @Test
    void ensure_isIdempotent() {
        subscriptionService.ensureActiveSubscription(accountId);
        Subscription again = subscriptionService.ensureActiveSubscription(accountId);
        assertEquals(1L, countActive()); // không tạo thêm
        assertNotNull(again.getId());
    }

    @Test
    void ensure_expiredPremium_createsNewFree() {
        Subscription paid = subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(60, ChronoUnit.DAYS))
                .endDate(Instant.now().minus(30, ChronoUnit.DAYS)) // đã hết hạn
                .pricePaid(100_000L)
                .storageGbGranted(10.0)
                .maxStorageGb(10.0)
                .flashcardLimitGranted(100)
                .questionLimitGranted(100)
                .summaryLimitGranted(100)
                .chatLimitGranted(100)
                .tierGranted(2)
                .build());

        Subscription healed = subscriptionService.ensureActiveSubscription(accountId);

        // Row cũ → EXPIRED, row mới → FREE
        assertEquals(SubscriptionStatus.EXPIRED, paid.getStatus());
        assertEquals("FREE", healed.getPlan().getName().toUpperCase());
        assertEquals(1L, countActive());
    }

    @Test
    void ensure_dedupesMultipleActive() {
        subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(null)
                .pricePaid(0L)
                .storageGbGranted(1.0)
                .maxStorageGb(1.0)
                .flashcardLimitGranted(0)
                .questionLimitGranted(0)
                .summaryLimitGranted(0)
                .chatLimitGranted(0)
                .tierGranted(0)
                .build());
        subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .pricePaid(100_000L)
                .storageGbGranted(10.0)
                .maxStorageGb(10.0)
                .flashcardLimitGranted(100)
                .questionLimitGranted(100)
                .summaryLimitGranted(100)
                .chatLimitGranted(100)
                .tierGranted(2)
                .build());

        Subscription kept = subscriptionService.ensureActiveSubscription(accountId);
        assertEquals(1L, countActive());
        assertEquals("PRO", kept.getPlan().getName().toUpperCase()); // giữ mới nhất (tier cao hơn)
        List<Subscription> upgraded = subscriptionRepository
                .findByAccountIdAndStatus(accountId, SubscriptionStatus.UPGRADED);
        assertEquals(1L, upgraded.size());
    }
}
