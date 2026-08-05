package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-1: proration chỉ có MỘT nguồn — SubscriptionService.calculateUpgradePreview.
 * Business rule: credit = ceil(ngày còn lại) × (pricePaid / durationDays)
 * — pricePaid là tiền THỰC SỰ đã trả, không phải giá niêm yết plan hiện tại.
 */
@SpringBootTest
class ProrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentPlanRepository paymentPlanRepository;
    @Autowired private SubscriptionService subscriptionService;

    private UUID accountId;
    private PaymentPlan freePlan;
    private PaymentPlan basicPlan;    // 100_000 / 30d, tier 1
    private PaymentPlan proPlan;      // 200_000 / 30d, tier 2
    private PaymentPlan premiumPlan;  // 300_000 / 30d, tier 3

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(Account.builder()
                .username("pror_" + UUID.randomUUID().toString().substring(0, 8))
                .email("pror_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .passwordHash("x")
                .build());
        accountId = account.getId();

        freePlan = plan("FREE", 0L, 0, -1, 0);
        basicPlan = plan("BASIC", 100_000L, 1, 30, 1);
        proPlan = plan("PRO", 200_000L, 2, 30, 2);
        premiumPlan = plan("PREMIUM", 300_000L, 3, 30, 3);
    }

    private PaymentPlan plan(String name, long price, int tier, int days, int order) {
        // Luôn tạo plan riêng cho test (suffix ngẫu nhiên) — tránh đụng plan seed
        // trong DB có giá khác (seed PREMIUM = 150k, test dùng 300k...).
        return paymentPlanRepository.save(PaymentPlan.builder()
                .name(name + "_" + UUID.randomUUID().toString().substring(0, 6))
                .price(price)
                .storageGb(1.0)
                .isActive(true)
                .durationDays(days)
                .tier(tier)
                .displayOrder(order)
                .build());
    }

    private Subscription sub(PaymentPlan plan, long pricePaid, Instant endDate) {
        return subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now().minus(10, ChronoUnit.DAYS))
                .endDate(endDate)
                .pricePaid(pricePaid)
                .storageGbGranted(1.0)
                .maxStorageGb(1.0)
                .aiQuestionsGranted(0)
                .flashcardLimitGranted(0)
                .questionLimitGranted(0)
                .summaryLimitGranted(0)
                .chatLimitGranted(0)
                .tierGranted(plan.getTier())
                .build());
    }

    @AfterEach
    void cleanup() {
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByAccountIdOrderByCreatedAtDesc(accountId));
        accountRepository.deleteById(accountId);
    }

    // ============ CASES ============

    @Test
    void firstPurchase_noSubscription_paysFullPrice() {
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, premiumPlan.getId());
        assertEquals(300_000L, r.getAmountToPay());
        assertEquals(0L, r.getRemainingDays());
        assertEquals(0L, r.getRemainingCredit());
        assertEquals("N/A", r.getCurrentPlanName());
    }

    @Test
    void proratedUpgrade_creditOnPricePaid() {
        // BASIC đã mua full 100k, còn 20 ngày
        sub(basicPlan, 100_000L, Instant.now().plus(20, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, proPlan.getId());
        // credit = 100k/30 × 20 = 66.667 → round 66_667
        long expectedCredit = Math.round(100_000.0 / 30 * 20);
        assertEquals(expectedCredit, r.getRemainingCredit());
        assertEquals(20L, r.getRemainingDays());
        assertEquals(Math.max(0, 200_000L - expectedCredit), r.getAmountToPay());
        assertTrue(r.getCurrentPlanName().startsWith("BASIC"));
    }

    @Test
    void secondUpgrade_usesPreviousPricePaid_notListedPrice() {
        // BASIC mua 100k → upgrade PRO giữa chừng, pricePaid = 100k (đã trả prorate trước đó)
        // Tạo trực tiếp: user đã ở PRO với pricePaid 50k (đã prorate từ lần trước)
        sub(proPlan, 50_000L, Instant.now().plus(15, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, premiumPlan.getId());
        // credit phải tính trên 50k (tiền thực trả) — KHÔNG phải 200k (giá niêm yết PRO)
        long expectedCredit = Math.round(50_000.0 / 30 * 15);
        assertEquals(expectedCredit, r.getRemainingCredit());
        assertEquals(300_000L - expectedCredit, r.getAmountToPay());
        assertTrue(r.getAmountToPay() > 200_000L - Math.round(200_000.0 / 30 * 15),
                "Nếu dùng giá niêm yết, amountToPay sẽ thấp hơn — bug");
    }

    @Test
    void discountedPlan_creditOnDiscountedPricePaid() {
        // User mua BASIC lúc khuyến mãi 50k (giá niêm yết 100k) → upgrade PRO
        sub(basicPlan, 50_000L, Instant.now().plus(10, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, proPlan.getId());
        long expectedCredit = Math.round(50_000.0 / 30 * 10);
        assertEquals(expectedCredit, r.getRemainingCredit()); // 50k thực trả, không phải 100k niêm yết
        assertEquals(200_000L - expectedCredit, r.getAmountToPay());
    }

    @Test
    void changedPlanPrice_creditOnOldPaidPrice() {
        // User mua PRO khi giá 200k → admin tăng giá lên 250k
        sub(proPlan, 200_000L, Instant.now().plus(10, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, premiumPlan.getId());
        long expectedCredit = Math.round(200_000.0 / 30 * 10); // vẫn 200k thực trả
        assertEquals(expectedCredit, r.getRemainingCredit());
        assertEquals(300_000L - expectedCredit, r.getAmountToPay());
    }

    @Test
    void samePlan_rejected() {
        sub(basicPlan, 100_000L, Instant.now().plus(20, ChronoUnit.DAYS));
        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.calculateUpgradePreview(accountId, basicPlan.getId()));
    }

    @Test
    void expiredSubscription_treatedAsNoActive() {
        // Sub hết hạn 1 ngày trước → không chặn downgrade, tính như mua mới
        sub(premiumPlan, 300_000L, Instant.now().minus(1, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, basicPlan.getId());
        assertEquals(basicPlan.getPrice(), r.getAmountToPay()); // price full của TARGET (basic 100k), không credit
        assertEquals(0L, r.getRemainingDays());
        assertEquals("N/A", r.getCurrentPlanName()); // sub expired không tính là current
    }

    @Test
    void amountToPay_neverNegative() {
        // BASIC full 100k, còn 30 ngày → credit ≈ 100k → amountToPay ≈ 0 (không âm)
        sub(basicPlan, 100_000L, Instant.now().plus(30, ChronoUnit.DAYS));
        UpgradePreviewResponse r = subscriptionService.calculateUpgradePreview(accountId, proPlan.getId());
        assertTrue(r.getAmountToPay() >= 0, "amountToPay không bao giờ âm");
        // credit ≈ 100k (full 30 ngày) → amountToPay = 200k − 100k = 100k
        assertEquals(200_000L - Math.round(100_000.0 / 30 * 30), r.getAmountToPay());
    }

    @Test
    void inactivePlan_rejected() {
        PaymentPlan inactive = paymentPlanRepository.save(PaymentPlan.builder()
                .name("DISABLED_" + UUID.randomUUID().toString().substring(0, 6))
                .price(50_000L)
                .storageGb(1.0)
                .isActive(false)
                .durationDays(30)
                .tier(5)
                .displayOrder(99)
                .build());
        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.calculateUpgradePreview(accountId, inactive.getId()));
    }
}
