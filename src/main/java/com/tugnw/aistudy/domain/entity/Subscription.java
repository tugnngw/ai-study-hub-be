package com.tugnw.aistudy.domain.entity;

import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private PaymentPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "price_paid", nullable = false)
    private Long pricePaid;

    @Column(name = "storage_gb_granted", nullable = false)
    private Double storageGbGranted = 1.0;

    // Quyền lợi storage thực tế — snapshot lúc tạo subscription, KHÔNG đọc
    // từ PaymentPlan khi check quota (plan có thể bị sửa sau).
    @Column(name = "max_storage_gb", nullable = false)
    private Double maxStorageGb = 1.0;

    @Column(name = "flashcard_limit_granted", nullable = false)
    @Builder.Default
    private Integer flashcardLimitGranted = 0;

    @Column(name = "question_limit_granted", nullable = false)
    @Builder.Default
    private Integer questionLimitGranted = 0;

    @Column(name = "summary_limit_granted", nullable = false)
    @Builder.Default
    private Integer summaryLimitGranted = 0;

    @Column(name = "chat_limit_granted", nullable = false)
    @Builder.Default
    private Integer chatLimitGranted = 0;

    @Column(name = "tier_granted", nullable = false)
    @Builder.Default
    private Integer tierGranted = 0;

    @Column(name = "auto_renew")
    @Builder.Default
    private Boolean autoRenew = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upgraded_to_subscription_id")
    private Subscription upgradedToSubscription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
