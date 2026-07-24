package com.tugnw.aistudy.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private String checkoutUrl;
    private Long orderCode;
    private Long amount;
    private Instant expiredAt;
    private String qrCode;
}
