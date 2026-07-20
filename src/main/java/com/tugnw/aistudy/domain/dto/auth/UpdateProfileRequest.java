package com.tugnw.aistudy.domain.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "Profile update request")
public record UpdateProfileRequest(
        @Size(max = 30, message = "Full name must not exceed 30 characters")
        @Schema(description = "Full name (optional)", example = "John Doe")
        String fullName,

        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @Schema(description = "Email address (optional)", example = "john@example.com")
        String email
) {}
