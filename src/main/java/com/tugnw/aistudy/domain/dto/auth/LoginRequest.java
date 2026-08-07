package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login request")
public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 255, message = "Username or email must not exceed 255 characters")
        @Schema(description = "Username or email", example = "john_doe")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 128 characters")
        @Schema(description = "Password", example = "SecurePass123")
        String password
) {
}
