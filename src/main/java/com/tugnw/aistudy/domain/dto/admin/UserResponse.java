package com.tugnw.aistudy.domain.dto.admin;

import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private AccountRole role;
    private AuthProvider provider;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isDeleted;
}