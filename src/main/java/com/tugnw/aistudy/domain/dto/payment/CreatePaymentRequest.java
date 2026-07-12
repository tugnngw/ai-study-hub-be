package com.tugnw.aistudy.domain.dto.payment;

import lombok.Data;
import java.util.UUID;

@Data
public class CreatePaymentRequest {
    private UUID planId;
    // userId sẽ được lấy từ Authentication, không nhận từ client
}
