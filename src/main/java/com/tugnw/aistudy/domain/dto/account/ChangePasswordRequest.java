package com.tugnw.aistudy.domain.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Change password request for the current authenticated user")
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Schema(description = "Current password", example = "CurrentPass123")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Schema(description = "New password", example = "NewPass123", minLength = 8)
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        @Schema(description = "Confirm new password", example = "NewPass123")
        String confirmPassword
) {
}