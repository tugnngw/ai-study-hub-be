package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.repository.FlashcardRepository;
import com.tugnw.aistudy.repository.QuestionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.QuizRepository;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final FlashcardRepository flashcardRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final DocumentRepository documentRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Check if user has quota for a specific feature
     * @param accountId User account ID
     * @param featureType Type of feature: "flashcard", "question", "summary"
     * @return true if allowed, false if exceeded quota
     */
    public boolean checkQuota(UUID accountId, String featureType) {
        return checkQuotaForGeneration(accountId, featureType, 1);
    }

    /**
     * Check if user has quota to generate a specific quantity
     * @param accountId User account ID
     * @param featureType Type of feature: "flashcard", "question", "summary"
     * @param quantity Number of items to be generated
     * @return true if allowed, false if would exceed quota
     */
    public boolean checkQuotaForGeneration(UUID accountId, String featureType, int quantity) {
        Optional<Subscription> activeSubscriptionOpt = subscriptionRepository.findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        
        if (activeSubscriptionOpt.isEmpty()) {
            log.warn("No active subscription found for account {}", accountId);
            return false;
        }

        Subscription subscription = activeSubscriptionOpt.get();
        PaymentPlan plan = subscription.getPlan();
        
        if (plan == null) {
            log.error("Subscription {} has no associated plan", subscription.getId());
            return false;
        }

        if (subscription.getEndDate().isBefore(Instant.now())) {
            log.warn("Subscription {} has expired for account {}", subscription.getId(), accountId);
            return false;
        }

        Integer limit = getPlanLimit(plan, featureType);
        if (limit == null) {
            log.warn("Plan {} does not have limit configured for feature {}", plan.getId(), featureType);
            return false;
        }

        if (limit == 0) {
            log.info("Feature {} is disabled for plan {}", featureType, plan.getName());
            return false;
        }

        if (limit == -1) {
            return true;
        }

        Integer currentUsage = getCurrentUsage(accountId, featureType);
        if (currentUsage + quantity > limit) {
            log.warn("Quota would be exceeded for feature {}: current={}, requested={}, limit={}", 
                featureType, currentUsage, quantity, limit);
            return false;
        }

        return true;
    }

    private Integer getPlanLimit(PaymentPlan plan, String featureType) {
        switch (featureType.toLowerCase()) {
            case "flashcard":
                return plan.getFlashcardLimit();
            case "question":
                return plan.getQuestionLimit();
            case "summary":
                return plan.getSummaryLimit();
            default:
                return null;
        }
    }

    private Integer getCurrentUsage(UUID accountId, String featureType) {
        switch (featureType.toLowerCase()) {
            case "flashcard":
                return (int) flashcardRepository.countByDocumentIdIn(documentRepository.findAllIdsByOwnerId(accountId));
            case "question":
                List<UUID> docIds = documentRepository.findAllIdsByOwnerId(accountId);
                List<UUID> quizIds = quizRepository.findAllIdsByDocumentIds(docIds);
                return quizIds.isEmpty() ? 0 : (int) questionRepository.countByQuizIdIn(quizIds);
            case "summary":
                return (int) documentRepository.countByOwnerIdAndSummaryIsNotNull(accountId);
            default:
                return 0;
        }
    }

    /**
     * Get remaining quota for a feature
     * @return -1 if unlimited, positive number if limited, 0 if disabled or exceeded
     */
    public int getRemainingQuota(UUID accountId, String featureType) {
        Optional<Subscription> activeSubscriptionOpt = subscriptionRepository.findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        
        if (activeSubscriptionOpt.isEmpty()) {
            return 0;
        }

        Subscription subscription = activeSubscriptionOpt.get();
        PaymentPlan plan = subscription.getPlan();
        
        if (plan == null || subscription.getEndDate().isBefore(Instant.now())) {
            return 0;
        }

        Integer limit = getPlanLimit(plan, featureType);
        if (limit == null || limit == 0) {
            return 0;
        }

        if (limit == -1) {
            return -1; // unlimited
        }

        Integer currentUsage = getCurrentUsage(accountId, featureType);
        return Math.max(0, limit - currentUsage);
    }

    /**
     * Get quota details for user
     */
    public QuotaDetails getQuotaDetails(UUID accountId) {
        Optional<Subscription> activeSubscriptionOpt = subscriptionRepository.findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        
        if (activeSubscriptionOpt.isEmpty()) {
            return QuotaDetails.noSubscription();
        }

        Subscription subscription = activeSubscriptionOpt.get();
        PaymentPlan plan = subscription.getPlan();
        
        if (plan == null) {
            return QuotaDetails.noPlan();
        }

        if (subscription.getEndDate().isBefore(Instant.now())) {
            return QuotaDetails.expired();
        }

        return QuotaDetails.builder()
                .planName(plan.getName())
                .storageGb(subscription.getStorageGbGranted())
                .aiQuestions(subscription.getAiQuestionsGranted())
                .flashcardLimit(plan.getFlashcardLimit())
                .questionLimit(plan.getQuestionLimit())
                .summaryLimit(plan.getSummaryLimit())
                .flashcardRemaining(getRemainingQuota(accountId, "flashcard"))
                .questionRemaining(getRemainingQuota(accountId, "question"))
                .summaryRemaining(getRemainingQuota(accountId, "summary"))
                .subscriptionEndDate(subscription.getEndDate())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaDetails {
        private String planName;
        private Double storageGb;
        private Integer aiQuestions;
        private Integer flashcardLimit;
        private Integer questionLimit;
        private Integer summaryLimit;
        private Integer flashcardRemaining;
        private Integer questionRemaining;
        private Integer summaryRemaining;
        private Instant subscriptionEndDate;
        private String status;

        public static QuotaDetails noSubscription() {
            return QuotaDetails.builder()
                    .status("NO_SUBSCRIPTION")
                    .build();
        }

        public static QuotaDetails noPlan() {
            return QuotaDetails.builder()
                    .status("NO_PLAN")
                    .build();
        }

        public static QuotaDetails expired() {
            return QuotaDetails.builder()
                    .status("EXPIRED")
                    .build();
        }
    }
}