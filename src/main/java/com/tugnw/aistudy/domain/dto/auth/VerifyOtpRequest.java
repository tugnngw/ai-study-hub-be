package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Verify OTP request")
public record VerifyOtpRequest(
        @NotBlank(message = "Email is required")
        @Schema(description = "Account email", example = "user@example.com")
        String email,

        @NotBlank(message = "OTP is required")
        @Schema(description = "6-digit OTP", example = "483921")
        String otp
) {
}
