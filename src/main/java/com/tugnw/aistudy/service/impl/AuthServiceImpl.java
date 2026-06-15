package com.tugnw.aistudy.service.impl;

import com.nimbusds.openid.connect.sdk.LogoutRequest;
import com.tugnw.aistudy.domain.dto.auth.AuthResponse;
import com.tugnw.aistudy.domain.dto.auth.LoginRequest;
import com.tugnw.aistudy.domain.dto.auth.RegisterRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.security.JwtTokenProvider;
import com.tugnw.aistudy.service.AuthService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
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
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;

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

        accountRepository.save(account);

        // Generate JWT tokens
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities()
        );

        String accessToken = jwtTokenProvider.generateToken(authentication);

        // Map account to response and add tokens
        AuthResponse response = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                response.userId(),
                response.username(),
                response.email(),
                response.fullName(),
                response.role(),
                accessToken,
                null, // refreshToken not implemented yet
                3600000L // 1 hour expiration in ms
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username());
        if (account == null) {
            throw new InvalidCredentialsException("Invalid username or password");
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

        // Map account to response and add tokens
        AuthResponse response = accountMapper.toAuthResponse(account);
        return new AuthResponse(
                response.userId(),
                response.username(),
                response.email(),
                response.fullName(),
                response.role(),
                accessToken,
                null, // refreshToken not implemented yet
                3600000L // 1 hour expiration in ms
        );
    }

    @Override
    public void logout(LogoutRequest request) {
        // Logout logic can be implemented here if needed
        // For JWT, logout is typically client-side (token deletion)
    }
}
