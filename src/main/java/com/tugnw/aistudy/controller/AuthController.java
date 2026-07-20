package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.auth.*;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.service.AuthService;
import com.tugnw.aistudy.service.PasswordResetService;
import com.tugnw.aistudy.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration successful.", authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful.", authService.login(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logout successful.", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully.", authService.refresh(request)));
    }

    @PostMapping("/send-verification")
    @Operation(summary = "Send verification email for an unverified account")
    public ResponseEntity<ApiResponse<Void>> sendVerification(@RequestParam @NotBlank @Email String email) {
        verificationService.sendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent.", null));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> resendVerification(Authentication authentication) {
        var userDetails = (com.tugnw.aistudy.security.CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getAccount().getEmail();
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("No email on file. Please update your email first."));
        }
        verificationService.resendVerification(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent.", null));
    }

    @PostMapping("/resend-verification-by-username")
    @Operation(summary = "Resend verification email by username (unauthenticated)")
    public ResponseEntity<ApiResponse<Void>> resendVerificationByUsername(@RequestParam @NotBlank String username) {
        verificationService.resendVerificationByUsername(username);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent.", null));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify email with token")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam @NotBlank String token) {
        verificationService.verify(token);
        return ResponseEntity.ok(ApiResponse.success("Email verification successful.", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a password reset email has been sent.", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a valid token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully.", null));
    }
}
