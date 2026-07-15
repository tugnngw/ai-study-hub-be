package com.tugnw.aistudy.service.impl;

import com.nimbusds.openid.connect.sdk.LogoutRequest;
import com.tugnw.aistudy.domain.dto.auth.AuthResponse;
import com.tugnw.aistudy.domain.dto.auth.LoginRequest;
import com.tugnw.aistudy.domain.dto.auth.RefreshTokenRequest;
import com.tugnw.aistudy.domain.dto.auth.RegisterRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.security.JwtTokenProvider;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.AuthService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;
    private final ActivityLogService activityLogService;
    private final PaymentPlanRepository paymentPlanRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    public AuthResponse register(RegisterRequest request) {
        // Check if username already exists
        if (accountRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(request.username())) {
            throw new InvalidCredentialsException("Username already exists");
        }

        // Create new account
        Account account = Account.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(AccountRole.USER)
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(Instant.now())
                .build();

        account = accountRepository.save(account);

        // Create FREE subscription for new user
        try {
            createFreeSubscription(account);
            log.info("Created FREE subscription for new user: {}", account.getUsername());
        } catch (Exception e) {
            log.error("Failed to create FREE subscription for user {}: {}", account.getUsername(), e.getMessage());
        }

        // Log activity for user registration
        activityLogService.logActivity(
                account.getId(),
                account.getUsername(),
                ActivityType.USER_REGISTER,
                "User registered a new account"
        );

        // Generate JWT tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities()
        );

        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        logTokenIssued("register", account, accessToken, refreshToken);

        // Map account to response and add tokens
        AuthResponse response = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                response.userId(),
                response.username(),
                response.email(),
                response.fullName(),
                response.role(),
                accessToken,
                refreshToken,
                3600000L // 1 hour expiration in ms
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username());
        if (account == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để biết thêm chi tiết.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // Update last login time
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        // Generate JWT tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities()
        );

        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        logTokenIssued("login", account, accessToken, refreshToken);

        // Map account to response and add tokens
        AuthResponse response = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                response.userId(),
                response.username(),
                response.email(),
                response.fullName(),
                response.role(),
                accessToken,
                refreshToken,
                3600000L // 1 hour expiration in ms
        );
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        // Validate the refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromJWT(refreshToken);
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw new InvalidTokenException("Account not found for refresh token");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTokenException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để biết thêm chi tiết.");
        }

        // Create authentication from the account
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities()
        );

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        logTokenIssued("refresh", account, newAccessToken, newRefreshToken);

        AuthResponse response = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                response.userId(),
                response.username(),
                response.email(),
                response.fullName(),
                response.role(),
                newAccessToken,
                newRefreshToken,
                (long) jwtTokenProvider.getJwtExpirationInMs()
        );
    }

    @Override
    public void logout(LogoutRequest request) {
        // Logout logic can be implemented here if needed
        // For JWT, logout is typically client-side (token deletion)
    }

    private void logTokenIssued(String flow, Account account, String accessToken, String refreshToken) {
        log.info("AUTH_TOKEN_ISSUED flow={} userId={} username={} accessTokenPresent={} accessTokenLength={} accessTokenPrefix={} refreshTokenPresent={} refreshTokenLength={} refreshTokenPrefix={}",
                flow,
                account.getId(),
                account.getUsername(),
                accessToken != null && !accessToken.isBlank(),
                accessToken == null ? 0 : accessToken.length(),
                tokenPrefix(accessToken),
                refreshToken != null && !refreshToken.isBlank(),
                refreshToken == null ? 0 : refreshToken.length(),
                tokenPrefix(refreshToken));
    }

    private String tokenPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "NONE";
        }
        return token.substring(0, Math.min(12, token.length())) + "...";
    }

    private void createFreeSubscription(Account account) {
        PaymentPlan freePlan = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(plan -> "FREE".equalsIgnoreCase(plan.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("FREE plan not found in database"));

        Subscription subscription = Subscription.builder()
                .accountId(account.getId())
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(null)
                .pricePaid(0L)
                .storageGbGranted(freePlan.getStorageGb() != null ? freePlan.getStorageGb() : 1.0)
                .aiQuestionsGranted(freePlan.getAiQuestions() != null ? freePlan.getAiQuestions() : 5)
                .autoRenew(false)
                .build();

        subscriptionRepository.save(subscription);
    }
}
