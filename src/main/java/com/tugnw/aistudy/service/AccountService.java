package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.account.AccountMeResponse;
import com.tugnw.aistudy.domain.dto.account.ChangePasswordRequest;
import com.tugnw.aistudy.domain.dto.account.UpdateProfileRequest;
import org.springframework.security.core.Authentication;

public interface AccountService {

    /**
     * Get the current authenticated user's profile.
     */
    AccountMeResponse getMe(Authentication authentication);

    /**
     * Update profile fields (fullName, email).
     * If email changes, emailVerified is reset to false and a verification
     * email is sent.  Email sending failure does not rollback the update.
     */
    AccountMeResponse updateProfile(Authentication authentication, UpdateProfileRequest request);

    /**
     * Change the current authenticated user's password.
     * Verifies currentPassword, applies the password policy, and persists
     * the BCrypt-encoded new password. Throws on wrong current password,
     * policy violation, new == current, or confirm mismatch.
     */
    void changePassword(Authentication authentication, ChangePasswordRequest request);
}
