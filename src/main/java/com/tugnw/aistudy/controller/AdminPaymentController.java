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
    public ResponseEntity<ApiResponse<Page<AdminTransactionResponse>>> getAllTransactions(Pageable pageable) {
        log.debug("[ADMIN_ACTION] Getting all payment transactions, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<AdminTransactionResponse> transactions = paymentService.getAllTransactions(pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get payment transactions by status")
    public ResponseEntity<ApiResponse<Page<AdminTransactionResponse>>> getTransactionsByStatus(
            @PathVariable PaymentStatus status,
            Pageable pageable) {
        log.debug("[ADMIN_ACTION] Getting transactions with status: {}", status);
        Page<AdminTransactionResponse> transactions = paymentService.getTransactionsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/user/{accountId}")
    @Operation(summary = "Get payment transactions by user account ID")
    public ResponseEntity<ApiResponse<Page<AdminTransactionResponse>>> getTransactionsByUser(
            @PathVariable UUID accountId,
            Pageable pageable) {
        log.debug("[ADMIN_ACTION] Getting transactions for user: {}", accountId);
        Page<AdminTransactionResponse> transactions = paymentService.getTransactionsByAccountId(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
}
