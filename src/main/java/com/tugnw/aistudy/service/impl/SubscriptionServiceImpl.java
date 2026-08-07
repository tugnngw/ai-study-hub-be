package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.domain.mapper.SubscriptionMapper;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Subscription createSubscription(UUID accountId, PaymentPlan plan, PaymentTransaction tx) {
        Instant now = Instant.now();

        // Cancel all existing ACTIVE subscriptions before creating new one
        for (Subscription oldSub : listActiveSubscriptions(accountId)) {
            oldSub.setStatus(SubscriptionStatus.UPGRADED);
            oldSub.setCancelledAt(now);
            subscriptionRepository.save(oldSub);
        }

        Subscription subscription = buildSubscription(accountId, plan, tx, now);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Invariant: account luôn có đúng 1 subscription ACTIVE chưa hết hạn.
     * Idempotent — lock account trước, chữa mọi lệch lạc:
     *   ACTIVE hết hạn          → EXPIRED
     *   nhiều ACTIVE hợp lệ     → giữ mới nhất, số còn lại → UPGRADED
     *   không có ACTIVE hợp lệ  → tạo FREE (copy entitlement từ FREE Plan)
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID accountId) {
        Instant now = Instant.now();
        return subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE).stream()
                .anyMatch(s -> s.getEndDate() == null || !s.getEndDate().isBefore(now));
    }

    @Override
    @Transactional
    public Subscription ensureActiveSubscription(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        // Lock account row — serializes mọi ensure/expiry/payment cho cùng account
        entityManager.lock(account, LockModeType.PESSIMISTIC_WRITE);

        Instant now = Instant.now();

        // 1) ACTIVE nhưng hết hạn → EXPIRED
        List<Subscription> expired = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE)
                .stream()
                .filter(s -> s.getEndDate() != null && s.getEndDate().isBefore(now))
                .toList();
        for (Subscription s : expired) {
            s.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(s);
        }

        // 2) Lọc ACTIVE còn hợp lệ (mutable list để sort)
        List<Subscription> valid = new ArrayList<>(subscriptionRepository
                .findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE)
                .stream()
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(now))
                .toList());

        // 3) Nhiều ACTIVE hợp lệ → giữ mới nhất
        if (valid.size() > 1) {
            valid.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            for (Subscription older : valid.subList(1, valid.size())) {
                older.setStatus(SubscriptionStatus.UPGRADED);
                older.setCancelledAt(now);
                subscriptionRepository.save(older);
            }
            valid = List.of(valid.get(0));
        }

        // 4) Không có ACTIVE hợp lệ → tạo FREE
        if (valid.isEmpty()) {
            PaymentPlan freePlan = findFreePlan();
            Subscription free = buildSubscription(accountId, freePlan, null, now);
            return subscriptionRepository.save(free);
        }

        return valid.get(0);
    }

    @Override
    public Optional<SubscriptionResponse> getActiveSubscription(UUID accountId) {
        List<Subscription> activeSubs = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
        if (activeSubs.isEmpty()) {
            return Optional.empty();
        }
        if (activeSubs.size() > 1) {
            activeSubs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            return Optional.of(subscriptionMapper.toResponse(activeSubs.get(0)));
        }
        return Optional.of(subscriptionMapper.toResponse(activeSubs.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public UpgradePreviewResponse calculateUpgradePreview(UUID accountId, UUID newPlanId) {
        List<Subscription> activeSubs = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
        Instant now = Instant.now();
        // Chỉ tính proration trên subscription THỰC SỰ còn hạn.
        // Sub ACTIVE đã hết hạn = như không có — không chặn downgrade/same-plan.
        Subscription currentSubscription = activeSubs.stream()
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(now))
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .orElse(null);

        PaymentPlan newPlan = paymentPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new IllegalArgumentException("New plan not found"));
        if (!Boolean.TRUE.equals(newPlan.getIsActive()))
            throw new IllegalArgumentException("This plan is not available");

        if (currentSubscription != null && currentSubscription.getPlan().getId().equals(newPlanId))
            throw new IllegalArgumentException("Cannot upgrade to the same plan");

        if (currentSubscription != null && newPlan.isDowngradeFrom(currentSubscription.getPlan()))
            throw new IllegalArgumentException("Cannot downgrade to a lower-tier plan while an active subscription exists.");

        long remainingDays = 0L;
        long remainingCredit = 0L;
        // durationDays = -1 means permanent (no expiration)
        Integer newDays = newPlan.getDurationDays();
        Instant newEndDate = (newDays != null && newDays >= 0) ? now.plus(newDays, ChronoUnit.DAYS) : null;

        if (currentSubscription != null) {
            // Proration — ceil(ngày còn lại), credit trên pricePaid (tiền THỰC SỰ đã trả,
            // không phải giá niêm yết hiện tại — tránh lỗ khi plan đổi giá/đã prorate).
            if (currentSubscription.getEndDate() != null && currentSubscription.getEndDate().isAfter(now)) {
                long remainingMillis = currentSubscription.getEndDate().toEpochMilli() - now.toEpochMilli();
                remainingDays = (long) Math.ceil((double) remainingMillis / 86_400_000L);
                int currentDuration = currentSubscription.getPlan().getDurationDays() != null
                        && currentSubscription.getPlan().getDurationDays() > 0
                        ? currentSubscription.getPlan().getDurationDays()
                        : 30;
                remainingCredit = Math.round(((double) currentSubscription.getPricePaid() / currentDuration) * remainingDays);
            }
            if (newDays != null && newDays >= 0) {
                newEndDate = newEndDate != null
                    ? newEndDate.plus(remainingDays, ChronoUnit.DAYS)
                    : now.plus(remainingDays + newDays, ChronoUnit.DAYS);
            }
        }

        long amountToPay = Math.max(0, newPlan.getPrice() - remainingCredit);

        return UpgradePreviewResponse.builder()
                .currentPlanName(currentSubscription != null ? currentSubscription.getPlan().getName() : "N/A")
                .newPlanName(newPlan.getName())
                .remainingDays(remainingDays)
                .remainingCredit(remainingCredit)
                .newPlanPrice(newPlan.getPrice())
                .amountToPay(amountToPay)
                .newEndDate(newEndDate)
                .build();
    }

    @Override
    public List<SubscriptionResponse> getSubscriptionHistory(UUID accountId) {
        return subscriptionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    // ============ HELPERS ============

    private List<Subscription> listActiveSubscriptions(UUID accountId) {
        return subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
    }

    private PaymentPlan findFreePlan() {
        return paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("FREE plan not configured"));
    }

    /** Build row từ plan — mọi entitlement snapshot (maxStorageGb) đều qua đây. */
    private Subscription buildSubscription(UUID accountId, PaymentPlan plan, PaymentTransaction tx, Instant now) {
        // durationDays = -1 means permanent (no expiration)
        Integer days = plan.getDurationDays();
        Instant endDate = (days != null && days >= 0) ? now.plus(days, ChronoUnit.DAYS) : null;

        return Subscription.builder()
                .accountId(accountId)
                .plan(plan)
                .paymentTransaction(tx)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(endDate)
                .pricePaid(plan.getPrice())
                .storageGbGranted(plan.getStorageGb())
                .maxStorageGb(plan.getStorageGb())
                .flashcardLimitGranted(plan.getFlashcardLimit() != null ? plan.getFlashcardLimit() : 0)
                .questionLimitGranted(plan.getQuestionLimit() != null ? plan.getQuestionLimit() : 0)
                .summaryLimitGranted(plan.getSummaryLimit() != null ? plan.getSummaryLimit() : 0)
                .chatLimitGranted(plan.getChatLimit() != null ? plan.getChatLimit() : 0)
                .tierGranted(plan.getTier() != null ? plan.getTier() : 0)
                .build();
    }
}
