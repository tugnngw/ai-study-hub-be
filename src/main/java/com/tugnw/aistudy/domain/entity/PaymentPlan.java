package com.tugnw.aistudy.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "payment_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "storage_gb")
    private Double storageGb;

    @Column(name = "ai_questions")
    private Integer aiQuestions;

    @Column(nullable = false)
    private Long price;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "tagline")
    private String tagline;

    @Column(name = "duration_days")
    private Integer durationDays;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "features")
    private String features;

    @Column(name = "is_popular")
    @Builder.Default
    private Boolean isPopular = false;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "flashcard_limit")
    @Builder.Default
    private Integer flashcardLimit = 0;

    @Column(name = "question_limit")
    @Builder.Default
    private Integer questionLimit = 0;

    @Column(name = "summary_limit")
    @Builder.Default
    private Integer summaryLimit = 0;

    @Column(name = "chat_limit")
    @Builder.Default
    private Integer chatLimit = 0;

    @Column(name = "tier", nullable = false)
    @Builder.Default
    private Integer tier = 0;

    /** Returns true if this plan is a higher tier than {@code other}. */
    public boolean isUpgradeFrom(PaymentPlan other) {
        return this.tier > other.tier;
    }

    /** Returns true if this plan is a lower tier than {@code other}. */
    public boolean isDowngradeFrom(PaymentPlan other) {
        return this.tier < other.tier;
    }

    /** Returns true if this plan is the same tier as {@code other}. */
    public boolean isSameTierAs(PaymentPlan other) {
        return this.tier.equals(other.tier);
    }
}
