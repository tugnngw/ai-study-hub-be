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

    /**
     * Đảm bảo account có đúng 1 subscription ACTIVE chưa hết hạn.
     * Idempotent + transactional + lock account (PESSIMISTIC_WRITE).
     * Là NƠI DUY NHẤT được phép tạo FREE subscription.
     * @return subscription ACTIVE hợp lệ
     */
    Subscription ensureActiveSubscription(UUID accountId);

    /** True khi account có subscription ACTIVE chưa hết hạn — dùng cho lazy-heal (không side-effect). */
    boolean hasActiveSubscription(UUID accountId);

    Optional<SubscriptionResponse> getActiveSubscription(UUID accountId);

    UpgradePreviewResponse calculateUpgradePreview(UUID accountId, UUID newPlanId);

    List<SubscriptionResponse> getSubscriptionHistory(UUID accountId);

}
