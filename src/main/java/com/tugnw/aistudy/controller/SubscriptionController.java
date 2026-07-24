package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.subscription.SubscriptionResponse;
import com.tugnw.aistudy.domain.dto.subscription.UpgradePreviewResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    private UUID userId(Authentication a) {return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @GetMapping("/my")
    public ApiResponse<?> getMySubscription(Authentication authentication) {
        return ApiResponse.success(subscriptionService.getActiveSubscription(userId(authentication)).orElse(null));
    }

    @GetMapping("/history")
    public ApiResponse<List<SubscriptionResponse>> getSubscriptionHistory(Authentication authentication) {
        return ApiResponse.success(subscriptionService.getSubscriptionHistory(userId(authentication)));
    }

    @GetMapping("/upgrade-preview")
    public ApiResponse<UpgradePreviewResponse> getUpgradePreview(
            @RequestParam UUID newPlanId,
            Authentication authentication) {
        return ApiResponse.success(subscriptionService.calculateUpgradePreview(userId(authentication), newPlanId));
    }

    @PostMapping("/upgrade")
    public ApiResponse<PaymentResponse> upgradeSubscription(
            @RequestParam UUID newPlanId,
            Authentication authentication) {
        return ApiResponse.success(paymentService.createPaymentLink(userId(authentication), newPlanId));
    }
}
