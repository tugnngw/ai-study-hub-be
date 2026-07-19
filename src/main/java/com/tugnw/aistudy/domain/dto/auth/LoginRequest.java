package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login request")
public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 50, message = "Username must not exceed 10 characters")
        @Schema(description = "Username or email", example = "john_doe")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 20 characters")
        @Schema(description = "Password", example = "SecurePass123")
        String password
) {
}
