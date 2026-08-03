package com.tugnw.aistudy.domain.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanRevenueStats {
    private String planName;
    private long revenue;
}
