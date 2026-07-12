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
    private Integer storageGb;

    @Column(name = "ai_questions")
    private Integer aiQuestions;

    @Column(nullable = false)
    private Long price;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
