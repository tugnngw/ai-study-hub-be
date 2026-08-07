package com.tugnw.aistudy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.payment.CreatePaymentRequest;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentStatusResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment and subscription endpoints")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    private UUID extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getAccount().getId();
        }
        return UUID.fromString(principal.toString());
    }

    @GetMapping("/plans")
    public ApiResponse<List<PaymentPlan>> getActivePlans() {
        return ApiResponse.success(paymentService.listActivePlans());
    }

    @PostMapping("/create")
    public ApiResponse<PaymentResponse> createPaymentLink(
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        // Chỉ gửi planId, server tự tính amount
        return ApiResponse.success(paymentService.createPaymentLink(userId, request.getPlanId()));
    }

    @GetMapping("/status/{orderCode}")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(@PathVariable Long orderCode) {
        return ApiResponse.success(paymentService.getPaymentStatus(orderCode));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/verify/{orderCode}")
    public ApiResponse<?> verifyPayment(@PathVariable Long orderCode) {
        log.info("Manual payment verification for orderCode: {}", orderCode);
        paymentService.verifyAndProcessPayment(orderCode);
        return ApiResponse.success("Payment verified successfully", null);
    }

    @PostMapping("/webhook")
    public ApiResponse<?> handleWebhook(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String signature = root.has("signature") ? root.get("signature").asText() : null;
            paymentService.handleWebhook(payload, signature);
            return ApiResponse.success("Webhook handling success", null);
        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/my-transactions")
    public ApiResponse<?> getMyTransactions(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ApiResponse.success(paymentService.getUserTransactions(userId));
    }

    @GetMapping("/my-subscription")
    public ApiResponse<?> getMySubscription(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return subscriptionService.getActiveSubscription(userId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }
}
