package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Reset password request")
public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        @Schema(description = "Password reset token")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Schema(description = "New password", example = "NewSecurePass123", minLength = 8, maxLength = 128)
        String newPassword,

        @NotBlank(message = "Password confirmation is required")
        @Schema(description = "Confirm new password", example = "NewSecurePass123")
        String confirmPassword
) {
}
