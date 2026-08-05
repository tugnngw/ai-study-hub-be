package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.quota.QuotaDetails;
import com.tugnw.aistudy.domain.dto.quota.StorageQuota;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.FeatureType;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.domain.mapper.QuotaDetailsMapper;
import com.tugnw.aistudy.repository.ChatMessageRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    private final DocumentRepository documentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QuotaDetailsMapper quotaDetailsMapper;
    private final StorageQuotaService storageQuotaService;

    private record ActivePlan(Subscription subscription, PaymentPlan plan, String planName) {}

    /**
     * Chỉ đọc subscription ACTIVE hợp lệ — KHÔNG fallback FREE.
     * Invariant "luôn có 1 ACTIVE" do SubscriptionService đảm bảo.
     */
    private ActivePlan resolvePlan(UUID accountId) {
        Optional<Subscription> subOpt = subscriptionRepository
                .findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        Subscription sub = subOpt.orElse(null);
        if (sub != null && sub.getEndDate() != null && sub.getEndDate().isBefore(Instant.now())) {
            sub = null;
        }
        if (sub == null) return null;
        return new ActivePlan(sub, null, sub.getPlan() != null ? sub.getPlan().getName() : "N/A");
    }

    private Integer getLimit(ActivePlan ap, FeatureType ft) {
        return ap.subscription != null ? ft.limit(ap.subscription) : ft.limit(ap.plan);
    }

    private int usage(FeatureType ft, UUID accountId) {
        return ft.currentUsage(documentRepository, chatSessionRepository, chatMessageRepository, accountId);
    }

    @Override
    public boolean checkQuota(UUID accountId, String featureType) {
        return checkQuotaForGeneration(accountId, featureType, 1);
    }

    @Override
    public boolean checkQuotaForGeneration(UUID accountId, String featureType, int quantity) {
        FeatureType ft = FeatureType.fromKey(featureType);
        ActivePlan ap = resolvePlan(accountId);
        if (ap == null) return false;
        Integer limit = getLimit(ap, ft);
        if (limit == null || limit == 0) return false;
        if (limit == -1) return true;
        return usage(ft, accountId) + quantity <= limit;
    }

    @Override
    public int getRemainingQuota(UUID accountId, String featureType) {
        FeatureType ft = FeatureType.fromKey(featureType);
        ActivePlan ap = resolvePlan(accountId);
        if (ap == null) return 0;
        Integer limit = getLimit(ap, ft);
        if (limit == null || limit == 0) return 0;
        if (limit == -1) return -1;
        return Math.max(0, limit - usage(ft, accountId));
    }

    @Override
    public QuotaDetails getQuotaDetails(UUID accountId) {
        ActivePlan ap = resolvePlan(accountId);
        if (ap == null) return QuotaDetails.noSubscription();
        QuotaDetails d = ap.subscription != null
                ? quotaDetailsMapper.toQuotaDetails(ap.subscription)//5
                : quotaDetailsMapper.toQuotaDetails(ap.plan);//5
        for (FeatureType ft : FeatureType.values()) {
            switch (ft) {
                case FLASHCARD -> d.setFlashcardRemaining(getRemainingQuota(accountId, ft.key()));
                case QUESTION -> d.setQuestionRemaining(getRemainingQuota(accountId, ft.key()));
                case SUMMARY -> d.setSummaryRemaining(getRemainingQuota(accountId, ft.key()));
                case CHAT -> d.setChatRemaining(getRemainingQuota(accountId, ft.key()));
            }
        }

        // Storage — StorageQuotaService là nơi duy nhất tính.
        StorageQuota sq = storageQuotaService.getQuota(accountId);
        d.setStorageUsedBytes(sq.storageUsedBytes());
        d.setStorageLimitBytes(sq.storageLimitBytes());
        d.setStorageRemainingBytes(sq.storageRemainingBytes());
        d.setOverQuota(sq.overQuota());
        return d;
    }
}
