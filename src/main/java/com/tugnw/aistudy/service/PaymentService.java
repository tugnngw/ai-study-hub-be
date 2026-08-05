package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.payment.AdminTransactionResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentStatusResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentTransactionResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentService {
    List<PaymentPlan> listActivePlans();

    PaymentResponse createPaymentLink(UUID userId, UUID planId);

    Optional<PaymentTransaction> getTransactionByOrderCode(Long orderCode);

    void handleWebhook(String payload, String signature);

    List<PaymentTransactionResponse> getUserTransactions(UUID userId);

    Page<AdminTransactionResponse> getAllTransactions(Pageable pageable);

    Page<AdminTransactionResponse> getTransactionsByStatus(PaymentStatus status, Pageable pageable);

    Page<AdminTransactionResponse> getTransactionsByAccountId(UUID accountId, Pageable pageable);

    void verifyAndProcessPayment(Long orderCode);

    PaymentStatusResponse getPaymentStatus(Long orderCode);
}
