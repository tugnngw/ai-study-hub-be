package com.tugnw.aistudy.domain.dto.payment;

import lombok.Data;

@Data
public class CreatePaymentRequest {
    private Long planId;
    // userId sẽ được lấy từ Authentication, không nhận từ client
}

