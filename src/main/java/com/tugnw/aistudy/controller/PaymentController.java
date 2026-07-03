package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.payment.CreatePaymentRequest;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/plans")
    public ResponseEntity<List<PaymentPlan>> getActivePlans() {
        return ResponseEntity.ok(paymentService.listActivePlans());
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPaymentLink(
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        // Trích xuất UUID userId an toàn từ CustomUserDetails hoặc Authentication
        Object principal = authentication.getPrincipal();
        UUID userId;
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails) {
            userId = ((com.tugnw.aistudy.security.CustomUserDetails) principal).getAccount().getId();
        } else {
            userId = UUID.fromString(principal.toString());
        }
        return ResponseEntity.ok(paymentService.createPaymentLink(userId, request.getPlanId()));
    }

    @GetMapping("/status/{orderCode}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable Long orderCode) {
        return paymentService.getTransactionByOrderCode(orderCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/verify/{orderCode}")
    public ResponseEntity<?> verifyPayment(@PathVariable Long orderCode) {
        log.info("Manual payment verification for orderCode: {}", orderCode);
        paymentService.verifyAndProcessPayment(orderCode);
        return ResponseEntity.ok().body("Payment verified successfully");
    }

    //  Webhook endpoint (Priority 1)
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload,
                                           @RequestHeader(value = "x-payos-signature", required = false) String signature) {
        try {
            paymentService.handleWebhook(payload, signature);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook handling failed", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-transactions")
    public ResponseEntity<?> getMyTransactions(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        UUID userId;
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails) {
            userId = ((com.tugnw.aistudy.security.CustomUserDetails) principal).getAccount().getId();
        } else {
            userId = UUID.fromString(principal.toString());
        }
        return ResponseEntity.ok(paymentService.getUserTransactions(userId));
    }
}
