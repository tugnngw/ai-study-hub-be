package com.tugnw.aistudy.domain.dto.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanResponse {
    private UUID id;
    private String name;
    private String tagline;
    private String description;
    private Long price;
    private Integer durationDays;
    private Double storageGb;
    private Integer aiQuestions;
    private List<String> features;
    private Boolean isPopular;
    private Integer displayOrder;
    private Integer flashcardLimit;
    private Integer questionLimit;
    private Integer summaryLimit;
    private Integer chatLimit;
    private Boolean isActive;
    private Long activeSubscriptionCount;
}
