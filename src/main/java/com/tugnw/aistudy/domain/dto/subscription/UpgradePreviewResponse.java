package com.tugnw.aistudy.domain.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpgradePreviewResponse {
    private String currentPlanName;
    private String newPlanName;
    private Long remainingDays;
    private Long remainingCredit;
    private Long newPlanPrice;
    private Long amountToPay;
    private Instant newEndDate;
}
