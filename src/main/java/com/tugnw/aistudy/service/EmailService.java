package com.tugnw.aistudy.service;

public interface EmailService {

    /**
     * Send a verification email to the given address.
     *
     * @param to       recipient email
     * @param username recipient display name
     * @param token    verification token embedded in the link
     */
    void sendVerificationEmail(String to, String username, String token);

    /**
     * Send a password-reset OTP email.
     *
     * @param to       recipient email
     * @param username recipient display name
     * @param otp      6-digit numeric OTP
     */
    void sendPasswordResetEmail(String to, String username, String otp);
}
