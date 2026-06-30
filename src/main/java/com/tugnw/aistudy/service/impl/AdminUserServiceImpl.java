package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.admin.UserResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.exception.ResourceNotFoundException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final AccountRepository accountRepository;

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("[DEBUG] getAllUsers - filtering by USER role only");
        Page<Account> accounts = accountRepository.findByRoleAndDeletedAtIsNull(
            com.tugnw.aistudy.domain.enums.AccountRole.USER, pageable);
        log.info("[DEBUG] Found {} USER accounts", accounts.getTotalElements());
        accounts.forEach(a -> log.info("[DEBUG] User: id={}, username={}, deletedAt={}", 
            a.getId(), a.getUsername(), a.getDeletedAt()));
        return accounts.map(this::toUserResponse);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return toUserResponse(account);
    }

    @Override
    @Transactional
    public UserResponse lockUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setStatus(AccountStatus.INACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse unlockUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse softDeleteUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setDeletedAt(Instant.now());
        account.setStatus(AccountStatus.SOFT_deleted);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse restoreUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (account.getDeletedAt() == null && account.getStatus() != AccountStatus.SOFT_deleted) {
            throw new ResourceNotFoundException("User is not marked as SOFT_deleted");
        }
        account.setDeletedAt(null);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse toggleStatus(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        // Toggle ACTIVE <-> INACTIVE
        if (account.getStatus() == com.tugnw.aistudy.domain.enums.AccountStatus.ACTIVE) {
            account.setStatus(com.tugnw.aistudy.domain.enums.AccountStatus.INACTIVE);
        } else if (account.getStatus() == com.tugnw.aistudy.domain.enums.AccountStatus.INACTIVE) {
            account.setStatus(com.tugnw.aistudy.domain.enums.AccountStatus.ACTIVE);
        } else {
            // If status is SOFT_deleted, keep it unchanged
        }
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return toUserResponse(saved);
    }

    private UserResponse toUserResponse(Account account) {
        return UserResponse.builder()
                .id(account.getId().toString())
                .username(account.getUsername())
                .email(account.getEmail())
                .fullName(account.getFullName())
                .avatarUrl(account.getAvatarUrl())
                .role(account.getRole())
                .status(account.getStatus())
                .authProvider(account.getAuthProvider())
                .providerId(account.getProviderId())
                .lastLoginAt(account.getLastLoginAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
    }
}