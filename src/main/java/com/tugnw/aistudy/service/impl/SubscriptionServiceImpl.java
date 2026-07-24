package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.domain.mapper.SubscriptionMapper;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Override
    @Transactional
    public Subscription createSubscription(UUID accountId, PaymentPlan plan, PaymentTransaction tx) {
        Instant now = Instant.now();
        
        // Cancel all existing ACTIVE subscriptions before creating new one
        List<Subscription> activeSubs = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
        for (Subscription oldSub : activeSubs) {
            oldSub.setStatus(SubscriptionStatus.UPGRADED);
            oldSub.setCancelledAt(now);
            subscriptionRepository.save(oldSub);
        }
        
        // durationDays = -1 means permanent (no expiration)
        Integer days = plan.getDurationDays();
        Instant endDate = (days != null && days >= 0) ? now.plus(days, ChronoUnit.DAYS) : null;

        Subscription subscription = Subscription.builder()
                .accountId(accountId)
                .plan(plan)
                .paymentTransaction(tx)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(endDate)
                .pricePaid(plan.getPrice())
                .storageGbGranted(plan.getStorageGb())
                .aiQuestionsGranted(plan.getAiQuestions())
                .flashcardLimitGranted(plan.getFlashcardLimit() != null ? plan.getFlashcardLimit() : 0)
                .questionLimitGranted(plan.getQuestionLimit() != null ? plan.getQuestionLimit() : 0)
                .summaryLimitGranted(plan.getSummaryLimit() != null ? plan.getSummaryLimit() : 0)
                .chatLimitGranted(plan.getChatLimit() != null ? plan.getChatLimit() : 0)
                .tierGranted(plan.getTier() != null ? plan.getTier() : 0)
                .build();

        return subscriptionRepository.save(subscription);
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
    public UpgradePreviewResponse calculateUpgradePreview(UUID accountId, UUID newPlanId) {
        List<Subscription> activeSubs = subscriptionRepository.findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
        Subscription currentSubscription = activeSubs.isEmpty() ? null : activeSubs.get(0); // Can be null if no active subscription

        PaymentPlan newPlan = paymentPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new IllegalArgumentException("New plan not found"));

        if (currentSubscription != null && currentSubscription.getPlan().getId().equals(newPlanId))
            throw new IllegalArgumentException("Cannot upgrade to the same plan");

        if (currentSubscription != null && newPlan.isDowngradeFrom(currentSubscription.getPlan()))
            throw new IllegalArgumentException("Cannot downgrade to a lower-tier plan while an active subscription exists.");

        long remainingDays = 0L;
        long remainingCredit = 0L;
        // durationDays = -1 means permanent (no expiration)
        Integer newDays = newPlan.getDurationDays();
        Instant newEndDate = (newDays != null && newDays >= 0) ? Instant.now().plus(newDays, ChronoUnit.DAYS) : null;

        if (currentSubscription != null) {
            // Proration logic
            Instant now = Instant.now();
            if (currentSubscription.getEndDate() != null && currentSubscription.getEndDate().isAfter(now)) {
                remainingDays = ChronoUnit.DAYS.between(now, currentSubscription.getEndDate());
                if (currentSubscription.getPlan().getDurationDays() != null && currentSubscription.getPlan().getDurationDays() > 0) {
                    long dailyRate = currentSubscription.getPricePaid() / currentSubscription.getPlan().getDurationDays();
                    remainingCredit = dailyRate * remainingDays;
                }
            }
            if (newDays != null && newDays >= 0) {
                newEndDate = newEndDate != null
                    ? newEndDate.plus(remainingDays, ChronoUnit.DAYS)
                    : Instant.now().plus(remainingDays + newDays, ChronoUnit.DAYS);
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
}
