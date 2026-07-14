package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    @Operation(summary = "Get current user's quota details", description = "Returns quota information for flashcard, question, and summary")
    public ResponseEntity<QuotaService.QuotaDetails> getQuotaDetails(Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        QuotaService.QuotaDetails details = quotaService.getQuotaDetails(userId);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/check/{featureType}")
    @Operation(summary = "Check if user has quota for a specific feature")
    public ResponseEntity<Boolean> checkQuota(
            @PathVariable String featureType,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        boolean hasQuota = quotaService.checkQuota(userId, featureType);
        return ResponseEntity.ok(hasQuota);
    }

    @GetMapping("/remaining/{featureType}")
    @Operation(summary = "Get remaining quota for a specific feature")
    public ResponseEntity<Integer> getRemainingQuota(
            @PathVariable String featureType,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        int remaining = quotaService.getRemainingQuota(userId, featureType);
        return ResponseEntity.ok(remaining);
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("User chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }
        throw new RuntimeException("Không thể xác định user");
    }
}
