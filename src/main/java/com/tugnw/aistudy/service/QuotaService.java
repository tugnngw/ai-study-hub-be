package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.FlashcardRepository;
import com.tugnw.aistudy.repository.QuestionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.QuizRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.ChatMessageRepository;
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
    private final PaymentPlanRepository paymentPlanRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    /**
     * Check if user has quota for a specific feature
     * @param accountId User account ID
     * @param featureType Type of feature: "flashcard", "question", "summary", "chat"
     * @return true if allowed, false if exceeded quota
     */
    public boolean checkQuota(UUID accountId, String featureType) {
        return checkQuotaForGeneration(accountId, featureType, 1);
    }

    /**
     * Check if user has quota to generate a specific quantity
     * @param accountId User account ID
     * @param featureType Type of feature: "flashcard", "question", "summary", "chat"
     * @param quantity Number of items to be generated
     * @return true if allowed, false if would exceed quota
     */
    public boolean checkQuotaForGeneration(UUID accountId, String featureType, int quantity) {
        Optional<Subscription> activeSubscriptionOpt = subscriptionRepository.findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        
        if (activeSubscriptionOpt.isEmpty()) {
            log.warn("No active subscription found for account {}, checking FREE plan fallback", accountId);
            // Try to get FREE plan
            List<PaymentPlan> freePlans = getFreePlanFromDatabase();
            if (freePlans.isEmpty()) {
                log.error("No FREE plan found in database");
                return false;
            }
            
            PaymentPlan freePlan = freePlans.get(0);
            return checkQuotaForFreePlan(accountId, featureType, quantity, freePlan);
        }

        Subscription subscription = activeSubscriptionOpt.get();

        if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(Instant.now())) {
            log.warn("Subscription {} has expired for account {}", subscription.getId(), accountId);
            return false;
        }

        Integer limit = getPlanLimitFromSubscription(subscription, featureType);
        if (limit == null) {
            log.warn("Subscription {} does not have limit for feature {}", subscription.getId(), featureType);
            return false;
        }

        if (limit == 0) {
            log.info("Feature {} is disabled for subscription {}", featureType, subscription.getId());
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
    
    private boolean checkQuotaForFreePlan(UUID accountId, String featureType, int quantity, PaymentPlan freePlan) {
        Integer limit = getPlanLimit(freePlan, featureType);
        if (limit == null) {
            log.warn("FREE plan does not have limit configured for feature {}", featureType);
            return false;
        }

        if (limit == 0) {
            log.info("Feature {} is disabled for FREE plan", featureType);
            return false;
        }

        if (limit == -1) {
            return true;
        }

        Integer currentUsage = getCurrentUsage(accountId, featureType);
        if (currentUsage + quantity > limit) {
            log.warn("FREE plan quota would be exceeded for feature {}: current={}, requested={}, limit={}", 
                featureType, currentUsage, quantity, limit);
            return false;
        }

        return true;
    }
    
    private List<PaymentPlan> getFreePlanFromDatabase() {
        return paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(plan -> "FREE".equalsIgnoreCase(plan.getName()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Integer getPlanLimitFromSubscription(Subscription subscription, String featureType) {
        switch (featureType.toLowerCase()) {
            case "flashcard":
                return subscription.getFlashcardLimitGranted();
            case "question":
                return subscription.getQuestionLimitGranted();
            case "summary":
                return subscription.getSummaryLimitGranted();
            case "chat":
                return subscription.getChatLimitGranted();
            default:
                return null;
        }
    }

    private Integer getPlanLimit(PaymentPlan plan, String featureType) {
        switch (featureType.toLowerCase()) {
            case "flashcard":
                return plan.getFlashcardLimit();
            case "question":
                return plan.getQuestionLimit();
            case "summary":
                return plan.getSummaryLimit();
            case "chat":
                return plan.getChatLimit();
            default:
                return null;
        }
    }

    private Integer getCurrentUsage(UUID accountId, String featureType) {
        switch (featureType.toLowerCase()) {
            case "flashcard":
                return (int) documentRepository.sumFlashcardGenerationsByOwnerId(accountId);
            case "summary":
                return (int) documentRepository.countByOwnerIdAndSummaryIsNotNull(accountId);
            case "question":
                return (int) documentRepository.sumQuizGenerationsByOwnerId(accountId);
            case "chat":
                List<UUID> chatDocIds = documentRepository.findAllIdsByOwnerId(accountId);
                List<UUID> sessionIds = chatSessionRepository.findSessionIdsByDocumentIds(chatDocIds);
                return sessionIds.isEmpty() ? 0 : (int) chatMessageRepository.countUserMessagesBySessionIds(sessionIds);
            default:
                throw new IllegalArgumentException("Unknown feature type: " + featureType);
        }
    }

    /**
     * Get remaining quota for a feature
     * @return -1 if unlimited, positive number if limited, 0 if disabled or exceeded
     */
    public int getRemainingQuota(UUID accountId, String featureType) {
        Optional<Subscription> activeSubscriptionOpt = subscriptionRepository.findFirstByAccountIdAndStatusOrderByEndDateDesc(accountId, SubscriptionStatus.ACTIVE);
        
        if (activeSubscriptionOpt.isEmpty()) {
            List<PaymentPlan> freePlans = getFreePlanFromDatabase();
            if (freePlans.isEmpty()) {
                log.error("No FREE plan found in database for quota check");
                return 0;
            }
            
            PaymentPlan freePlan = freePlans.get(0);
            Integer limit = getPlanLimit(freePlan, featureType);
            if (limit == null || limit == 0) {
                return 0;
            }
            
            if (limit == -1) {
                return -1;
            }
            
            Integer currentUsage = getCurrentUsage(accountId, featureType);
            return Math.max(0, limit - currentUsage);
        }

        Subscription subscription = activeSubscriptionOpt.get();

        if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(Instant.now())) {
            return 0;
        }

        Integer limit = getPlanLimitFromSubscription(subscription, featureType);
        if (limit == null || limit == 0) {
            return 0;
        }

        if (limit == -1) {
            return -1;
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
            List<PaymentPlan> freePlans = getFreePlanFromDatabase();
            if (freePlans.isEmpty()) {
                return QuotaDetails.noSubscription();
            }
            
            PaymentPlan freePlan = freePlans.get(0);
            return QuotaDetails.builder()
                    .planName("FREE")
                    .storageGb(freePlan.getStorageGb())
                    .aiQuestions(freePlan.getAiQuestions())
                    .flashcardLimit(freePlan.getFlashcardLimit())
                    .questionLimit(freePlan.getQuestionLimit())
                    .summaryLimit(freePlan.getSummaryLimit())
                    .chatLimit(freePlan.getChatLimit())
                    .flashcardRemaining(getRemainingQuota(accountId, "flashcard"))
                    .questionRemaining(getRemainingQuota(accountId, "question"))
                    .summaryRemaining(getRemainingQuota(accountId, "summary"))
                    .chatRemaining(getRemainingQuota(accountId, "chat"))
                    .subscriptionEndDate(null)
                    .build();
        }

        Subscription subscription = activeSubscriptionOpt.get();

        if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(Instant.now())) {
            return QuotaDetails.expired();
        }

        return QuotaDetails.builder()
                .planName(subscription.getPlan() != null ? subscription.getPlan().getName() : "N/A")
                .storageGb(subscription.getStorageGbGranted())
                .aiQuestions(subscription.getAiQuestionsGranted())
                .flashcardLimit(subscription.getFlashcardLimitGranted())
                .questionLimit(subscription.getQuestionLimitGranted())
                .summaryLimit(subscription.getSummaryLimitGranted())
                .chatLimit(subscription.getChatLimitGranted())
                .flashcardRemaining(getRemainingQuota(accountId, "flashcard"))
                .questionRemaining(getRemainingQuota(accountId, "question"))
                .summaryRemaining(getRemainingQuota(accountId, "summary"))
                .chatRemaining(getRemainingQuota(accountId, "chat"))
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
        private Integer chatLimit;
        private Integer flashcardRemaining;
        private Integer questionRemaining;
        private Integer summaryRemaining;
        private Integer chatRemaining;
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