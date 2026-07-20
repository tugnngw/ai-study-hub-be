package com.tugnw.aistudy.service;

public interface VerificationService {

    /**
     * Create a new verification token for the given account.
     * Any existing active token is invalidated first.
     * Sends the verification email via {@link EmailService}.
     */
    void sendVerificationEmail(String email);

    /**
     * Same as {@link #sendVerificationEmail(String)} but for already-authenticated users.
     * The caller must ensure the current user matches {@code requesterId}.
     */
    void resendVerification(String email);

    /**
     * Look up account by username and resend verification email.
     * Silently returns if account not found or already verified (anti-enumeration).
     */
    void resendVerificationByUsername(String username);

    /**
     * Validate a verification token and mark the account as email-verified.
     *
     * @param token the verification token string
     * @throws com.tugnw.aistudy.exception.InvalidTokenException if token is expired, already used, or invalid
     */
    void verify(String token);
}
