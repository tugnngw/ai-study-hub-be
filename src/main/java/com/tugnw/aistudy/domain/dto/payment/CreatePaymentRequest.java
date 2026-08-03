package com.tugnw.aistudy.domain.dto.payment;

import lombok.Data;
import java.util.UUID;

@Data
public class CreatePaymentRequest {
    private UUID planId;
}
