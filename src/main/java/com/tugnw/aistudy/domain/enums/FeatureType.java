package com.tugnw.aistudy.domain.enums;

import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.repository.ChatMessageRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public enum FeatureType {

    FLASHCARD("flashcard",
            Subscription::getFlashcardLimitGranted,
            PaymentPlan::getFlashcardLimit,
            (doc, cs, cm, id) -> (int) doc.sumFlashcardGenerationsByOwnerId(id)),

    QUESTION("question",
            Subscription::getQuestionLimitGranted,
            PaymentPlan::getQuestionLimit,
            (doc, cs, cm, id) -> (int) doc.sumQuizGenerationsByOwnerId(id)),

    SUMMARY("summary",
            Subscription::getSummaryLimitGranted,
            PaymentPlan::getSummaryLimit,
            (doc, cs, cm, id) -> (int) doc.countByOwnerIdAndSummaryIsNotNull(id)),

    CHAT("chat",
            Subscription::getChatLimitGranted,
            PaymentPlan::getChatLimit,
            (doc, cs, cm, id) -> {
                List<UUID> docIds = doc.findAllIdsByOwnerId(id);
                List<UUID> sessionIds = cs.findSessionIdsByDocumentIds(docIds);
                return sessionIds.isEmpty() ? 0 : (int) cm.countUserMessagesBySessionIds(sessionIds);
            });

    private final String key;
    private final Function<Subscription, Integer> subLimit;
    private final Function<PaymentPlan, Integer> planLimit;
    private final UsageCounter usageCounter;

    FeatureType(String key, Function<Subscription, Integer> subLimit,
                Function<PaymentPlan, Integer> planLimit, UsageCounter usageCounter) {
        this.key = key;
        this.subLimit = subLimit;
        this.planLimit = planLimit;
        this.usageCounter = usageCounter;
    }

    public String key() { return key; }
    public Integer limit(Subscription s) { return subLimit.apply(s); }
    public Integer limit(PaymentPlan p) { return planLimit.apply(p); }
    public int currentUsage(DocumentRepository doc, ChatSessionRepository cs, ChatMessageRepository cm, UUID accountId) {
        return usageCounter.count(doc, cs, cm, accountId);
    }

    public static FeatureType fromKey(String key) {
        for (var ft : values()) {
            if (ft.key.equalsIgnoreCase(key)) return ft;
        }
        throw new IllegalArgumentException("Unknown feature type: " + key);
    }

    @FunctionalInterface
    public interface UsageCounter {
        int count(DocumentRepository doc, ChatSessionRepository cs, ChatMessageRepository cm, UUID accountId);
    }
}
