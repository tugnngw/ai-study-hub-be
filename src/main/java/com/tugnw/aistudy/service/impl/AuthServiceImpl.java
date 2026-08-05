package com.tugnw.aistudy.service.impl;

import com.nimbusds.openid.connect.sdk.LogoutRequest;
import com.tugnw.aistudy.domain.dto.auth.AuthResponse;
import com.tugnw.aistudy.domain.dto.auth.LoginRequest;
import com.tugnw.aistudy.domain.dto.auth.RefreshTokenRequest;
import com.tugnw.aistudy.domain.dto.auth.RegisterRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.exception.EmailNotVerifiedException;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.security.JwtTokenProvider;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.AuthService;
import com.tugnw.aistudy.service.SubscriptionService;
import com.tugnw.aistudy.service.VerificationService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;
    private final ActivityLogService activityLogService;
    private final VerificationService verificationService;
    private final SubscriptionService subscriptionService;

    @Value("${app.verification.auto-send-on-register:true}")
    private boolean autoSendOnRegister;

    @Override
    public AuthResponse register(RegisterRequest request) {
        // Check if username already exists
        if (accountRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(request.username()))
            throw new InvalidCredentialsException("Username already exists");

        // Normalize email if provided
        String normalizedEmail = null;
        if (request.email() != null && !request.email().isBlank()) {
            normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

            // Check if email already exists
            if (accountRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail))
                throw new InvalidCredentialsException("Email already exists");
        }

        // Create new account
        Account account = Account.builder()
                .username(request.username())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(AccountRole.USER)
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .build();

        account = accountRepository.save(account);

        // Invariant: account luôn có đúng 1 subscription ACTIVE.
        // Nơi duy nhất tạo FREE là SubscriptionService.ensureActiveSubscription.
        subscriptionService.ensureActiveSubscription(account.getId());

        // Log activity for user registration
        activityLogService.logActivity(
                account.getId(),
                account.getUsername(),
                ActivityType.USER_REGISTER,
                "User registered a new account");

        if (normalizedEmail != null) {
            // Email provided → user must verify before logging in
            if (autoSendOnRegister)
                verificationService.sendVerificationEmail(normalizedEmail);
            return buildAuthResponse(account, null, null);
        }

        // No email → auto-login immediately
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities());

        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(account, accessToken, refreshToken);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username());
        if (account == null)
            throw new InvalidCredentialsException("Invalid username or password");

        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new InvalidCredentialsException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để biết thêm chi tiết.");

        // Verify password
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash()))
            throw new InvalidCredentialsException("Invalid username or password");

        // Verify email if account has an email
        if (account.getEmail() != null && !account.isEmailVerified())
            throw new EmailNotVerifiedException("Email chưa được xác thực. Vui lòng kiểm tra hộp thư hoặc yêu cầu gửi lại email xác thực.");

        // Update last login time
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        // Self-heal account legacy — đảm bảo luôn có subscription ACTIVE
        subscriptionService.ensureActiveSubscription(account.getId());

        // Generate JWT tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities());

        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(account, accessToken, refreshToken);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        // Validate the refresh token
        if (!jwtTokenProvider.validateToken(refreshToken))
            throw new InvalidTokenException("Invalid or expired refresh token");

        String username = jwtTokenProvider.getUsernameFromJWT(refreshToken);
        Account account = accountRepository.findByUsername(username);
        if (account == null)
            throw new InvalidTokenException("Account not found for refresh token");

        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new InvalidTokenException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để biết thêm chi tiết.");

        // Create authentication from the account
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities());

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        return buildAuthResponse(account, newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(LogoutRequest request) {
        // Logout logic can be implemented here if needed
        // For JWT, logout is typically client-side (token deletion)
    }

    // ============ HELPER METHODS ============

    private AuthResponse buildAuthResponse(Account account, String accessToken, String refreshToken) {
        AuthResponse mapped = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                mapped.userId(),
                mapped.username(),
                mapped.email(),
                mapped.fullName(),
                mapped.role(),
                accessToken,
                refreshToken,
                accessToken != null ? (long) jwtTokenProvider.getJwtExpirationInMs() : 0L,
                account.isEmailVerified());
    }

}
