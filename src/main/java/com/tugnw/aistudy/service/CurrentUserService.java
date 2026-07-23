package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.security.CustomUserDetails;

import java.util.UUID;

public interface CurrentUserService {
    UUID getCurrentUserId();

    Account getCurrentAccount();

    CustomUserDetails getCurrentUser();
}
