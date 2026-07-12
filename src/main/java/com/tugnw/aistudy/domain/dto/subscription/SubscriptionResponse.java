package com.tugnw.aistudy.domain.dto.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {
    private String id;
    private UUID planId;
    private String planName;
    private String status;
    private Instant startDate;
    private Instant endDate;
    private Long pricePaid;
    private Double storageGbGranted;
    private Integer aiQuestionsGranted;
    private Long daysRemaining;
}
