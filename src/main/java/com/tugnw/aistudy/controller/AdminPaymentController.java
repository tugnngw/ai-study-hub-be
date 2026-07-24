package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.payment.AdminTransactionResponse;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import com.tugnw.aistudy.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/transactions")
@Tag(name = "Admin Payment Management", description = "Endpoints for managing payment transactions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Get all payment transactions with pagination")
    public ApiResponse<Page<AdminTransactionResponse>> getAllTransactions(Pageable pageable) {
        Page<AdminTransactionResponse> transactions = paymentService.getAllTransactions(pageable);
        return ApiResponse.success(transactions);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get payment transactions by status")
    public ApiResponse<Page<AdminTransactionResponse>> getTransactionsByStatus(
            @PathVariable PaymentStatus status,
            Pageable pageable) {
        Page<AdminTransactionResponse> transactions = paymentService.getTransactionsByStatus(status, pageable);
        return ApiResponse.success(transactions);
    }

    @GetMapping("/user/{accountId}")
    @Operation(summary = "Get payment transactions by user account ID")
    public ApiResponse<Page<AdminTransactionResponse>> getTransactionsByUser(
            @PathVariable UUID accountId,
            Pageable pageable) {
        Page<AdminTransactionResponse> transactions = paymentService.getTransactionsByAccountId(accountId, pageable);
        return ApiResponse.success(transactions);
    }
}
