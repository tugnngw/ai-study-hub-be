package com.tugnw.aistudy.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private String checkoutUrl;
    private Long orderCode; // LONG
    private Long amount;
}
