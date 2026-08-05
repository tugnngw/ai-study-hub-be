package com.tugnw.aistudy.domain.dto.quota;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaDetails {
    private String planName;
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

    // Storage — backend tính, frontend chỉ hiển thị
    private Long storageUsedBytes;
    private Long storageLimitBytes;
    private Long storageRemainingBytes;
    private Boolean overQuota;

    public static QuotaDetails noSubscription() {
        return QuotaDetails.builder().status("NO_SUBSCRIPTION").build();
    }

    public static QuotaDetails noPlan() {
        return QuotaDetails.builder().status("NO_PLAN").build();
    }

    public static QuotaDetails expired() {
        return QuotaDetails.builder().status("EXPIRED").build();
    }
}
