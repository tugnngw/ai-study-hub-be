package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
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
    public ResponseEntity<ApiResponse<Account>> getCurrentUser(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Account account = accountRepository.findByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", account));
    }
}
