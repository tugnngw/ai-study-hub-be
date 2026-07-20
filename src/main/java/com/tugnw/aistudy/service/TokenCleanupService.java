package com.tugnw.aistudy.service;

/**
 * Periodic cleanup of expired tokens.
 */
public interface TokenCleanupService {

    /** Delete all expired verification tokens. */
    void cleanExpiredVerificationTokens();

    /** Delete all expired password reset tokens. */
    void cleanExpiredPasswordResetTokens();
}
