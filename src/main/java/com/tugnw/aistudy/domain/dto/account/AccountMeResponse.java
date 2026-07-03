package com.tugnw.aistudy.domain.dto.account;

import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.Plan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountMeResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private AccountRole role;
    private AccountStatus status;
    private Plan plan;
    private Integer storageGb;
    private Instant createdAt;
    private Instant updatedAt;
}
