package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration request")
public record RegisterRequest(
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @Schema(description = "Email address (optional)", example = "john@example.com")
        String email,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 10 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
        @Schema(description = "Username", example = "john_doe", minLength = 3, maxLength = 50)
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 6 and 20 characters")
        @Schema(description = "Password", example = "SecurePass123", minLength = 8, maxLength = 128)
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 30, message = "Full name must not exceed 30 characters")
        @Schema(description = "Full name", example = "John Doe", maxLength = 30)
        String fullName
) {
}
