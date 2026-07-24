package com.tugnw.aistudy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.payment.CreatePaymentRequest;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/plans")
    public ApiResponse<List<PaymentPlan>> getActivePlans() {
        return ApiResponse.success(paymentService.listActivePlans());
    }

    @PostMapping("/create")
    public ApiResponse<PaymentResponse> createPaymentLink(
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        // Trích xuất UUID userId an toàn từ CustomUserDetails hoặc Authentication
        Object principal = authentication.getPrincipal();
        UUID userId;
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getAccount().getId();
        } else {
            userId = UUID.fromString(principal.toString());
        }
        return ApiResponse.success(paymentService.createPaymentLink(userId, request.getPlanId()));
    }

    @GetMapping("/status/{orderCode}")
    public ApiResponse<?> getPaymentStatus(@PathVariable Long orderCode) {
        return paymentService.getTransactionByOrderCode(orderCode)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @PostMapping("/verify/{orderCode}")
    public ApiResponse<?> verifyPayment(@PathVariable Long orderCode) {
        log.info("Manual payment verification for orderCode: {}", orderCode);
        paymentService.verifyAndProcessPayment(orderCode);
        return  ApiResponse.success("Payment verified successfully", null);
    }

    //  Webhook endpoint (Priority 1)
    @PostMapping("/webhook")
    public ApiResponse<?> handleWebhook(@RequestBody String payload) {
        try {
            // Extract signature from JSON body (PayOS sends it inside the payload, not as header)
            JsonNode root = objectMapper.readTree(payload);
            String signature = root.has("signature") ? root.get("signature").asText() : null;
            paymentService.handleWebhook(payload, signature);
            return ApiResponse.success("Webhook handling success", null);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/my-transactions")
    public ApiResponse<?> getMyTransactions(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        UUID userId;
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getAccount().getId();
        } else {
            userId = UUID.fromString(principal.toString());
        }
        return ApiResponse.success(paymentService.getUserTransactions(userId));
    }

    @GetMapping("/my-subscription")
    public ApiResponse<?> getMySubscription(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        UUID userId;
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getAccount().getId();
        }else {
            userId = UUID.fromString(principal.toString());
        }
        return subscriptionService.getActiveSubscription(userId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }
}
