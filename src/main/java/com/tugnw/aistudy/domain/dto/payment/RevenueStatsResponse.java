package com.tugnw.aistudy.domain.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RevenueStatsResponse {
    private long totalRevenue;
    private long monthlyRevenue;
    private long weeklyRevenue;
    private long totalPaidTransactions;
    private long totalFailedTransactions;
    private double successRate;
}
