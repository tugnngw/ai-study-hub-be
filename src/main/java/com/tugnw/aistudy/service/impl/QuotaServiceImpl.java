package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.quota.QuotaDetails;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.FeatureType;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.domain.mapper.QuotaDetailsMapper;
import com.tugnw.aistudy.repository.*;
import com.tugnw.aistudy.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    private final DocumentRepository documentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentPlanRepository paymentPlanRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QuotaDetailsMapper quotaDetailsMapper;

    private record ActivePlan(Subscription subscription, PaymentPlan plan, String planName) {}

    private ActivePlan resolvePlan(UUID accountId) {
        Optional<Subscription> subOpt = subscriptionRepository
                .findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        if (subOpt.isEmpty()) {
            List<PaymentPlan> freePlans = paymentPlanRepository.findByIsActiveTrue().stream()
                    .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                    .toList();
            if (freePlans.isEmpty()) return null;
            return new ActivePlan(null, freePlans.get(0), "FREE");
        }
        Subscription sub = subOpt.get();
        if (sub.getEndDate() != null && sub.getEndDate().isBefore(Instant.now())) return null;
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
                ? quotaDetailsMapper.toQuotaDetails(ap.subscription)
                : quotaDetailsMapper.toQuotaDetails(ap.plan);
        d.setStorageUsedBytes(documentRepository.sumFileSizeByOwnerId(accountId));
        
        // Calculate storage metrics
        long totalBytes = (long) (d.getStorageGb() * 1024 * 1024 * 1024);
        long freeBytes = Math.max(0, totalBytes - d.getStorageUsedBytes());
        d.setStorageTotalBytes(totalBytes);
        d.setStorageFreeBytes(freeBytes);
        d.setStorageUsagePercent(totalBytes > 0 ? (double) d.getStorageUsedBytes() * 100 / totalBytes : 0);
        d.setFormattedStorageUsed(formatBytes(d.getStorageUsedBytes()));
        d.setFormattedStorageTotal(formatBytes(totalBytes));
        d.setFormattedStorageFree(formatBytes(freeBytes));

        for (FeatureType ft : FeatureType.values()) {
            switch (ft) {
                case FLASHCARD -> d.setFlashcardRemaining(getRemainingQuota(accountId, ft.key()));
                case QUESTION -> d.setQuestionRemaining(getRemainingQuota(accountId, ft.key()));
                case SUMMARY -> d.setSummaryRemaining(getRemainingQuota(accountId, ft.key()));
                case CHAT -> d.setChatRemaining(getRemainingQuota(accountId, ft.key()));
            }
        }
        return d;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        double b = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        while (b >= 1024 && unitIndex < units.length - 1) {
            b /= 1024;
            unitIndex++;
        }
        return unitIndex == 0 ? String.format("%.0f %s", b, units[unitIndex]) : String.format("%.1f %s", b, units[unitIndex]);
    }
}
