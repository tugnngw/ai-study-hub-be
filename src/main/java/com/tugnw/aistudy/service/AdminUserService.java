package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.admin.UserResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(UUID id);
    UserResponse lockUser(UUID id);
    UserResponse unlockUser(UUID id);
    UserResponse softDeleteUser(UUID id);
    UserResponse restoreUser(UUID id);
    UserResponse toggleStatus(UUID id);
}