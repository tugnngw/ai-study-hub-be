package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.admin.UserResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.mapper.AccountMapper;
import com.tugnw.aistudy.exception.ResourceNotFoundException;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        Page<Account> accounts = accountRepository.findByRoleAndDeletedAtIsNull(
            AccountRole.USER, pageable);
        return accounts.map(accountMapper::toUserResponse);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return accountMapper.toUserResponse(account);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public UserResponse lockUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setStatus(AccountStatus.INACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return accountMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse unlockUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return accountMapper.toUserResponse(saved);
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
        return accountMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse restoreUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        if (account.getDeletedAt() == null && account.getStatus() != AccountStatus.SOFT_deleted)
            throw new ResourceNotFoundException("User is not marked as SOFT_deleted");
        account.setDeletedAt(null);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdatedAt(Instant.now());
        Account saved = accountRepository.save(account);
        return accountMapper.toUserResponse(saved);
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
        return accountMapper.toUserResponse(saved);
    }


    @Override
    @Transactional
    public void hardDeleteUser(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (account.getRole() == AccountRole.ADMIN)
            throw new IllegalArgumentException("Cannot delete admin accounts.");

        accountRepository.delete(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getSoftDeletedAccounts() {
        return accountRepository.findByDeletedAtIsNotNull().stream()
                .map(accountMapper::toUserResponse)
                .toList();
    }
}