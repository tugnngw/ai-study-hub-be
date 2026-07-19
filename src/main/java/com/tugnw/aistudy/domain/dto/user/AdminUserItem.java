package com.tugnw.aistudy.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserItem {
    private String id;
    private String name;
    private String email;
    private String status;
    private String plan;
    private String role;
    private String createdAt;
    private String lastLoginAt;
}
