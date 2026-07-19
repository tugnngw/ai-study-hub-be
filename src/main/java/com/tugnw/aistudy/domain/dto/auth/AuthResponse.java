package com.tugnw.aistudy.domain.dto.auth;

import com.tugnw.aistudy.domain.enums.AccountRole;

import java.util.UUID;

public record AuthResponse(UUID userId, String username, String email, String fullName, AccountRole role, String accessToken, String refreshToken, Long expiresIn) {
}

