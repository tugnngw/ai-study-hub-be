package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.auth.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout();

    AuthResponse refresh(RefreshTokenRequest request);

    void updateProfile(String username, UpdateProfileRequest request);
}
