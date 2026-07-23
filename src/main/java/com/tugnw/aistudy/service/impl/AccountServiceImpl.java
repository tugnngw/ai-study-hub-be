package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.account.UpdateProfileRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
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
    private final AccountMapper accountMapper;

    @Override
    public AccountMeResponse getMe(Authentication authentication) {
        Account account = loadAccount(authentication);
        return accountMapper.toAccountMeResponse(account);
    }

    @Override
    @Transactional
    public AccountMeResponse updateProfile(Authentication authentication, UpdateProfileRequest request) {
        Account account = loadAccount(authentication);

        // Update fullName
        if (request.fullName() != null && !request.fullName().isBlank()
                && !request.fullName().equals(account.getFullName()))
            account.setFullName(request.fullName().trim());

        // Update email
        // Only process when a non-empty email is explicitly provided
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim().toLowerCase(Locale.ROOT);

            // Skip if same as current
            if (!newEmail.equals(account.getEmail())) {
                // Duplicate check — exclude self by ID
                Optional<Account> existing = accountRepository
                        .findByEmailIgnoreCaseAndDeletedAtIsNull(newEmail);
                if (existing.isPresent() && !existing.get().getId().equals(account.getId()))
                    throw new InvalidCredentialsException("Email already in use");

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
        return accountMapper.toAccountMeResponse(account);
    }

    // ============ HELPER METHODS ============

    private Account loadAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new RuntimeException("Authentication required");
        String username = authentication.getName();
        Account account = accountRepository.findByUsername(username);
        if (account == null)
            throw new RuntimeException("Account not found: " + username);
        return account;
    }

}
