package com.tugnw.aistudy.domain.dto.admin;

import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import com.tugnw.aistudy.domain.enums.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private AccountRole role;
    private AccountStatus status;
    private AuthProvider authProvider;
    private String providerId;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private Plan plan;
    private Long usedStorageBytes;

    public long getRemainingDays() {
        if (deletedAt == null) return 0;
        long remaining = 30 - Duration.between(deletedAt, Instant.now()).toDays();
        return Math.max(0, remaining);
    }
}
