package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AccountRepository accountRepository;

    @Override
    public Page<Account> getAllAccounts(String role, String status, Pageable pageable) {
        if (role != null && !role.isEmpty()) {
            return accountRepository.findByRoleAndDeletedAtIsNull(
                AccountRole.valueOf(role), pageable);
        }
        if (status != null && !status.isEmpty()) {
            return accountRepository.findByStatusAndDeletedAtIsNull(
                AccountStatus.valueOf(status), pageable);
        }
        return accountRepository.findAll(pageable);
    }

    @Override
    public Account getAccountById(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
    }
}