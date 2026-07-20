package com.tugnw.aistudy.service;

public interface VerificationService {

    /**
     * Send a verification email.  If an active (unverified, unexpired)
     * token already exists for this account the same token is reused,
     * so in-flight links stay valid.
     */
    void sendVerificationEmail(String email);

    /**
     * Resend verification for an authenticated user (by email).
     */
    void resendVerification(String email);

    /**
     * Resend verification by username (unauthenticated, public endpoint).
     */
    void resendVerificationByUsername(String username);

    /**
     * Validate a verification token and mark the account as email-verified.
     *
     * @throws com.tugnw.aistudy.exception.InvalidTokenException if token is unknown
     * @throws com.tugnw.aistudy.exception.TokenExpiredException if token has expired
     */
    void verify(String token);
}
