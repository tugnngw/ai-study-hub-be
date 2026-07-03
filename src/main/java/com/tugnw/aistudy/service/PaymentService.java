package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentService {
    List<PaymentPlan> listActivePlans();
    PaymentResponse createPaymentLink(UUID userId, Long planId);
    Optional<PaymentTransaction> getTransactionByOrderCode(Long orderCode);
    void handleWebhook(String payload, String signature);
}
