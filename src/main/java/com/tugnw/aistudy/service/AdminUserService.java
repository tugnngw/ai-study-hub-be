package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.admin.UserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    /** Admin đặt lại mật khẩu cho user — reuse PasswordEncoder, không đổi flow auth. */
    void resetPassword(UUID id, String newPassword);
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(UUID id);
    UserResponse lockUser(UUID id);
    UserResponse unlockUser(UUID id);
    UserResponse softDeleteUser(UUID id);
    UserResponse restoreUser(UUID id);
    UserResponse toggleStatus(UUID id);
    void hardDeleteUser(UUID id);
    List<UserResponse> getSoftDeletedAccounts();
}