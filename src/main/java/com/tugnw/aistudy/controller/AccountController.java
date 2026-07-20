package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.auth.UpdateProfileRequest;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.service.AuthService;
import com.tugnw.aistudy.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;

import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Account endpoints")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountRepository accountRepository;
    private final QuotaService quotaService;
    private final AuthService authService;

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get current authenticated user details")
    public ResponseEntity<ApiResponse<AccountMeResponse>> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            account = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }
        
        AccountMeResponse response = AccountMeResponse.builder()
                .id(account.getId().toString())
                .username(account.getUsername())
                .email(account.getEmail())
                .fullName(account.getFullName())
                .avatarUrl(account.getAvatarUrl())
                .role(account.getRole())
                .status(account.getStatus())
                .emailVerified(account.isEmailVerified())
                .plan(account.getPlan())
                .storageGb(getEffectiveStorageGb(account.getId()))
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
        
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile", description = "Update fullName and/or email for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        authService.updateProfile(username, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated.", null));
    }

    private Double getEffectiveStorageGb(UUID accountId) {
        try {
            QuotaService.QuotaDetails quota = quotaService.getQuotaDetails(accountId);
            Double storageGb = quota.getStorageGb();
            if (storageGb != null) {
                return storageGb;
            }
        } catch (Exception e) {
            log.warn("Failed to get quota for account {}, fallback to account.storageGb", accountId);
        }
        return null;
    }
}
