package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    Page<Account> getAllAccounts(String role, String status, Pageable pageable);
    Account getAccountById(java.util.UUID id);
    // other admin ops can be added later
}