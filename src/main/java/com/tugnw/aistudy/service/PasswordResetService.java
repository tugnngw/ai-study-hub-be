package com.tugnw.aistudy.service;

public interface PasswordResetService {

    /**
     * Initiate a password reset for the given email.
     * Silently returns if the email does not exist (prevents enumeration).
     * Creates a reset token and sends an email via {@link EmailService}.
     */
    void requestReset(String email);

    /**
     * Reset the password using a valid token.
     *
     * @param token           the reset token
     * @param newPassword     the new plain-text password
     * @param confirmPassword password confirmation
     * @throws com.tugnw.aistudy.exception.PasswordMismatchException if passwords do not match
     * @throws com.tugnw.aistudy.exception.PasswordReuseException    if new password equals current
     * @throws com.tugnw.aistudy.exception.InvalidTokenException     if token not found
     * @throws com.tugnw.aistudy.exception.TokenExpiredException     if token expired
     * @throws com.tugnw.aistudy.exception.TokenAlreadyUsedException if token already used
     */
    void resetPassword(String token, String newPassword, String confirmPassword);
}
