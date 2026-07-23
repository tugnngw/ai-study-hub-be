package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    @Override
    public CustomUserDetails getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user))
            throw new AccessDeniedException("User not authenticated");
        return user;
    }

    @Override
    public UUID getCurrentUserId() {
        return getCurrentUser().getAccount().getId();
    }

    @Override
    public Account getCurrentAccount() {
        return getCurrentUser().getAccount();
    }
}