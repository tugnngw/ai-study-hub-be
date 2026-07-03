package com.tugnw.aistudy.domain.dto.payment;

import com.tugnw.aistudy.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTransactionResponse {
    private String id;
    private UUID accountId;
    private String userEmail;
    private String userName;
    private String planName;
    private Long amount;
    private PaymentStatus status;
    private String description;
    private String transactionId;
    private Long payosOrderCode;
    private String paymentMethod;
    private Instant createdAt;
    private Instant updatedAt;
}
