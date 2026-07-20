package com.tugnw.aistudy.service;

public interface PasswordResetService {

    /**
     * Initiate a password reset: generate/reuse a PASSWORD_RESET OTP
     * and send it via email.  Always returns silently — never reveals
     * whether the account exists.
     */
    void requestReset(String email);

    /**
     * Verify that the OTP is valid for the given email.
     * Does NOT consume the token — that happens at reset.
     */
    void verifyOtp(String email, String otp);

    /**
     * Reset the password: validate the OTP, update the password,
     * consume the token, clean up any remaining PASSWORD_RESET tokens.
     */
    void resetPassword(String email, String otp, String newPassword);
}
