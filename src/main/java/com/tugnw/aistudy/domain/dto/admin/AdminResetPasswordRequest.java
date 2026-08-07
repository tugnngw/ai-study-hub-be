package com.tugnw.aistudy.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin reset password request")
public record AdminResetPasswordRequest(
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Schema(description = "New password", example = "NewPass123", minLength = 8, maxLength = 128)
        String newPassword
) {
}
