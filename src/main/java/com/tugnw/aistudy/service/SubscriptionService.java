package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.entity.Subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionService {
    Subscription createSubscription(UUID accountId, PaymentPlan plan, PaymentTransaction tx);

    Optional<SubscriptionResponse> getActiveSubscription(UUID accountId);

    UpgradePreviewResponse calculateUpgradePreview(UUID accountId, UUID newPlanId);

    Subscription upgradeSubscription(UUID accountId, UUID newPlanId, PaymentTransaction tx);

    List<SubscriptionResponse> getSubscriptionHistory(UUID accountId);

    void expireSubscriptions();
}
