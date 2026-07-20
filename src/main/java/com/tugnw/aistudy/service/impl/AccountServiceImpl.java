package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.account.UpdateProfileRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.service.AccountService;
import com.tugnw.aistudy.service.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final VerificationService verificationService;

    @Override
    public AccountMeResponse getMe(Authentication authentication) {
        Account account = loadAccount(authentication);
        return toResponse(account);
    }

    @Override
    @Transactional
    public AccountMeResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        Account account = loadAccount(authentication);

        // ── Update fullName ─────────────────────────────────────────────
        if (request.fullName() != null && !request.fullName().isBlank()
                && !request.fullName().equals(account.getFullName())) {
            account.setFullName(request.fullName().trim());
        }

        // ── Update email ────────────────────────────────────────────────
        // Only process when a non-empty email is explicitly provided
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim().toLowerCase(Locale.ROOT);

            // Skip if same as current
            if (!newEmail.equals(account.getEmail())) {
                // Duplicate check — exclude self by ID
                Optional<Account> existing = accountRepository
                        .findByEmailIgnoreCaseAndDeletedAtIsNull(newEmail);
                if (existing.isPresent() && !existing.get().getId().equals(account.getId())) {
                    throw new InvalidCredentialsException("Email already in use");
                }

                account.setEmail(newEmail);
                account.setEmailVerified(false);

                // Send verification email — failure does NOT rollback
                try {
                    verificationService.sendVerificationEmail(newEmail);
                } catch (Exception e) {
                    log.error("Failed to send verification email to {} after profile update", newEmail, e);
                }
            }
        }

        accountRepository.save(account);
        log.info("Profile updated for account {}", account.getId());
        return toResponse(account);
    }

    private Account loadAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Authentication required");
        }
        String username = authentication.getName();
        Account account = accountRepository.findByUsername(username);
        if (account == null) {
            throw new RuntimeException("Account not found: " + username);
        }
        return account;
    }

    private AccountMeResponse toResponse(Account account) {
        return AccountMeResponse.builder()
                .id(account.getId().toString())
                .username(account.getUsername())
                .email(account.getEmail())
                .fullName(account.getFullName())
                .avatarUrl(account.getAvatarUrl())
                .role(account.getRole())
                .status(account.getStatus())
                .plan(account.getPlan())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .emailVerified(account.isEmailVerified())
                .build();
    }
}
