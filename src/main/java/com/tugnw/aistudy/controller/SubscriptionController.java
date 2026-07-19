package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "User subscription endpoints")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @GetMapping("/my")
    public ResponseEntity<?> getMySubscription(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(subscriptionService.getActiveSubscription(userId).orElse(null));
    }

    @GetMapping("/history")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionHistory(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(subscriptionService.getSubscriptionHistory(userId));
    }

    @GetMapping("/upgrade-preview")
    public ResponseEntity<UpgradePreviewResponse> getUpgradePreview(
            @RequestParam UUID newPlanId,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(subscriptionService.calculateUpgradePreview(userId, newPlanId));
    }

    @PostMapping("/upgrade")
    public ResponseEntity<PaymentResponse> upgradeSubscription(
            @RequestParam UUID newPlanId,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(paymentService.createPaymentLink(userId, newPlanId));
    }

    private UUID extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getAccount().getId();
        }
        return UUID.fromString(principal.toString());
    }
}
