package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Reset password request")
public record ResetPasswordRequest(
        @NotBlank(message = "Email is required")
        @Schema(description = "Account email", example = "user@example.com")
        String email,

        @NotBlank(message = "OTP is required")
        @Schema(description = "6-digit OTP", example = "483921")
        String otp,

        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 128, message = "Password must be between 6 and 128 characters")
        @Schema(description = "New password", example = "NewSecurePass123")
        String newPassword
) {
}
