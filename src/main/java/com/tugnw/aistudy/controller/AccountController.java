package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.account.UpdateProfileRequest;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
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

    private final AccountService accountService;

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get current authenticated user details")
    public ResponseEntity<ApiResponse<AccountMeResponse>> getCurrentUser(Authentication authentication) {
        AccountMeResponse response = accountService.getMe(authentication);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile", description = "Update full name and/or email. If email changes, a verification email is sent.")
    public ResponseEntity<ApiResponse<AccountMeResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        AccountMeResponse response = accountService.updateProfile(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }
}
