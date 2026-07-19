package com.tugnw.aistudy.domain.dto.payment;

import lombok.Data;

@Data
public class PaymentTransactionDto {
    private Long id;
    private String userId;
    private String planName;
    private Long orderCode;
    private Long amount;
    private String status;
    private String description;
    private String transactionId;
    private String createdAt;
    private String updatedAt;
}
