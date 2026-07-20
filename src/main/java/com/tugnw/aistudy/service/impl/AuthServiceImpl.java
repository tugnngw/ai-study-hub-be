package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.auth.*;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.exception.InvalidTokenException;
import com.tugnw.aistudy.exception.ResourceNotFoundException;
import com.tugnw.aistudy.exception.EmailNotVerifiedException;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.repository.VerificationTokenRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.security.JwtTokenProvider;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.AuthService;
import com.tugnw.aistudy.service.VerificationService;
import com.tugnw.aistudy.config.VerificationProperties;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;
    private final ActivityLogService activityLogService;
    private final PaymentPlanRepository paymentPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VerificationService verificationService;
    private final VerificationProperties verificationProperties;
    private final VerificationTokenRepository verificationTokenRepository;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if username already exists
        if (accountRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(request.username())) {
            throw new InvalidCredentialsException("Username already exists");
        }

        // Normalize email if provided
        String normalizedEmail = null;
        if (request.email() != null && !request.email().isBlank()) {
            normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

            // Check if email already exists
            if (accountRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
                throw new InvalidCredentialsException("Email already exists");
            }
        }

        // Create new account
        Account account = Account.builder()
                .username(request.username())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(AccountRole.USER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .lastLoginAt(Instant.now())
                .build();

        account = accountRepository.save(account);

        // Create FREE subscription for new user
        createFreeSubscription(account);
        log.info("Created FREE subscription for new user: {}", account.getUsername());

        // Log activity for user registration
        activityLogService.logActivity(
                account.getId(),
                account.getUsername(),
                ActivityType.USER_REGISTER,
                "User registered a new account"
        );

        if (normalizedEmail != null) {
            // Email provided → user must verify before logging in
            if (verificationProperties.isAutoSendOnRegister()) {
                verificationService.sendVerificationEmail(normalizedEmail);
            }
            log.info("Email verification required for account {}", account.getId());
            return buildAuthResponse(account, null, null);
        }

        // No email → auto-login immediately
        String accessToken = jwtTokenProvider.generateToken(toAuthentication(account));
        String refreshToken = jwtTokenProvider.generateRefreshToken(toAuthentication(account));

        log.info("AUTH_TOKEN_ISSUED flow=register userId={} username={}", account.getId(), account.getUsername());
        return buildAuthResponse(account, accessToken, refreshToken);
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

        // Verify email if account has an email
        if (account.getEmail() != null && !account.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email chưa được xác thực. Vui lòng kiểm tra hộp thư hoặc yêu cầu gửi lại email xác thực.");
        }

        // Update last login time
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.generateToken(toAuthentication(account));
        String refreshToken = jwtTokenProvider.generateRefreshToken(toAuthentication(account));

        log.info("AUTH_TOKEN_ISSUED flow=login userId={} username={}", account.getId(), account.getUsername());
        return buildAuthResponse(account, accessToken, refreshToken);
    }

    @Override
    @Transactional
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

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateToken(toAuthentication(account));
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(toAuthentication(account));

        log.info("AUTH_TOKEN_ISSUED flow=refresh userId={} username={}", account.getId(), account.getUsername());
        return buildAuthResponse(account, newAccessToken, newRefreshToken);
    }

    @Override
    public void logout() {
        // JWT is stateless — logout is handled client-side by discarding the token.
        log.debug("Logout requested (no-op, stateless JWT)");
    }

    @Override
    @Transactional
    public void updateProfile(String username, UpdateProfileRequest request) {
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw new ResourceNotFoundException("Account not found");
        }

        // -- Full name --
        if (request.fullName() != null) {
            account.setFullName(request.fullName().trim());
        }

        // -- Email --
        if (request.email() != null) {
            if (request.email().isBlank()) {
                // Email explicitly removed
                account.setEmail(null);
                account.setEmailVerified(false);
                accountRepository.save(account);
                verificationTokenRepository.deleteByAccount(account);
            } else {
                String newEmail = request.email().trim().toLowerCase(Locale.ROOT);

                // Duplicate check — exclude self by ID
                Optional<Account> existingByEmail = accountRepository
                        .findByEmailIgnoreCaseAndDeletedAtIsNull(newEmail);
                if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(account.getId())) {
                    throw new InvalidCredentialsException("Email already in use");
                }

                if (newEmail.equals(account.getEmail())) {
                    log.info("Email unchanged for account {}", account.getId());
                } else {
                    account.setEmail(newEmail);
                    account.setEmailVerified(false);

                    // Persist before performing side effects
                    accountRepository.save(account);

                    // Delete old verification tokens
                    verificationTokenRepository.deleteByAccount(account);

                    // sendVerificationEmail internally creates a new token
                    if (verificationProperties.isAutoSendOnRegister()) {
                        verificationService.sendVerificationEmail(newEmail);
                    }
                }
            }
        }

        accountRepository.save(account);
        log.info("Profile updated for account {}", account.getId());
    }

    /**
     * Build a {@link UsernamePasswordAuthenticationToken} from an account.
     */
    private Authentication toAuthentication(Account account) {
        CustomUserDetails principal = new CustomUserDetails(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    /**
     * Build an {@link AuthResponse} from account and tokens.
     */
    private AuthResponse buildAuthResponse(Account account, String accessToken, String refreshToken) {
        AuthResponse mapped = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                mapped.userId(),
                mapped.username(),
                mapped.email(),
                mapped.fullName(),
                mapped.role(),
                account.isEmailVerified(),
                accessToken,
                refreshToken,
                (long) jwtTokenProvider.getJwtExpirationInMs()
        );
    }

    private void createFreeSubscription(Account account) {
        PaymentPlan freePlan = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(plan -> "FREE".equalsIgnoreCase(plan.getName()))
                .findFirst()
                .orElse(null);

        if (freePlan == null) {
            log.warn("FREE plan not found in database — no subscription created for user: {}", account.getUsername());
            return;
        }

        Subscription subscription = Subscription.builder()
                .accountId(account.getId())
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(null)
                .pricePaid(0L)
                .storageGbGranted(freePlan.getStorageGb() != null ? freePlan.getStorageGb() : 1.0)
                .aiQuestionsGranted(freePlan.getAiQuestions() != null ? freePlan.getAiQuestions() : 5)
                .flashcardLimitGranted(freePlan.getFlashcardLimit() != null ? freePlan.getFlashcardLimit() : 0)
                .questionLimitGranted(freePlan.getQuestionLimit() != null ? freePlan.getQuestionLimit() : 0)
                .summaryLimitGranted(freePlan.getSummaryLimit() != null ? freePlan.getSummaryLimit() : 0)
                .chatLimitGranted(freePlan.getChatLimit() != null ? freePlan.getChatLimit() : 0)
                .tierGranted(freePlan.getTier() != null ? freePlan.getTier() : 0)
                .autoRenew(false)
                .build();

        subscriptionRepository.save(subscription);
    }
}
