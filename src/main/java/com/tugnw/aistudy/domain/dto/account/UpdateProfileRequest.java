package com.tugnw.aistudy.domain.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Profile update request")
public record UpdateProfileRequest(
        @Size(max = 30, message = "Full name must not exceed 30 characters")
        @Schema(description = "New full name", example = "John Doe", maxLength = 30)
        String fullName,

        @Size(max = 255, message = "Email must not exceed 255 characters")
        @jakarta.validation.constraints.Pattern(
                regexp = "^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$",
                message = "Email must be valid"
        )
        @Schema(description = "New email", example = "john@example.com")
        String email
) {
}
