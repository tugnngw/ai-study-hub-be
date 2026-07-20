package com.tugnw.aistudy.domain.dto.auth;

import com.tugnw.aistudy.domain.enums.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Authentication response with JWT tokens")
public record AuthResponse(
        @Schema(description = "User ID", example = "d7ff12cf-...")
        UUID userId,

        @Schema(description = "Username", example = "john_doe")
        String username,

        @Schema(description = "Email", example = "john@example.com")
        String email,

        @Schema(description = "Full name", example = "John Doe")
        String fullName,

        @Schema(description = "Account role", example = "USER")
        AccountRole role,

        @Schema(description = "Email verified", example = "false")
        boolean emailVerified,

        @Schema(description = "JWT access token")
        String accessToken,

        @Schema(description = "JWT refresh token")
        String refreshToken,

        @Schema(description = "Token expiration in milliseconds", example = "900000")
        Long expiresIn
) {
}
