package com.tugnw.aistudy.domain.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private Long id;
    private String accountId;
    private String userEmail;
    private String userName;
    private Long planId;
    private String planName;
    private Long amount;
    private String status;
    private String description;
    private String transactionId;
    private Long payosOrderCode;
    private Instant createdAt;
    private Instant updatedAt;
}
