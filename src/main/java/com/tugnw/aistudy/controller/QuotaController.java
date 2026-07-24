package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.quota.QuotaDetails;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/quota")
@Tag(name = "Quota", description = "User quota management")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class QuotaController {

    private final QuotaService quotaService;

    private UUID userId(Authentication a) {return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @GetMapping
    @Operation(summary = "Get current user's quota details", description = "Returns quota information for flashcard, question, and summary")
    public ApiResponse<QuotaDetails> getQuotaDetails(Authentication authentication) {
        return ApiResponse.success(quotaService.getQuotaDetails(userId(authentication)));
    }

    @GetMapping("/check/{featureType}")
    @Operation(summary = "Check if user has quota for a specific feature")
    public ApiResponse<Boolean> checkQuota(
            @PathVariable String featureType,
            Authentication authentication) {
        return ApiResponse.success(quotaService.checkQuota(userId(authentication), featureType));
    }

    @GetMapping("/remaining/{featureType}")
    @Operation(summary = "Get remaining quota for a specific feature")
    public ApiResponse<Integer> getRemainingQuota(
            @PathVariable String featureType,
            Authentication authentication) {
        return ApiResponse.success(quotaService.getRemainingQuota(userId(authentication), featureType));
    }

}
