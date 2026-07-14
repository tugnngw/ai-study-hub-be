package com.tugnw.aistudy.domain.dto.plan;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePlanRequest {
    private String name;

    private String tagline;

    private String description;

    @Min(value = 0, message = "Price must be greater than or equal to 0")
    private Long price;

    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    private Double storageGb;

    private Integer aiQuestions;

    private List<String> features;

    private Boolean isPopular;

    private Integer displayOrder;

    private Boolean isActive;

    private Integer flashcardLimit;

    private Integer questionLimit;

    private Integer summaryLimit;
}
