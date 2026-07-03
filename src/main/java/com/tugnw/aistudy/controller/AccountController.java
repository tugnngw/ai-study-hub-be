package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.repository.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Account endpoints")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

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
                .plan(account.getPlan())
                .storageGb(account.getStorageGb())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
        
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }
}
