package com.tugnw.aistudy.domain.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionStatsResponse {
    private long totalTransactions;
    private long successfulTransactions;
    private double successRate;
}
