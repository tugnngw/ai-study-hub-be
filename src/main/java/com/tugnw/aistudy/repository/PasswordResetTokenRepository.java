package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    boolean existsByToken(String token);

    /**
     * Find the most recent unused, non-expired reset token for an account.
     */
    @Query("""
            SELECT prt FROM PasswordResetToken prt
            WHERE prt.account = :account
              AND prt.usedAt IS NULL
              AND prt.expiresAt > :now
            ORDER BY prt.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetToken> findActiveByAccount(@Param("account") Account account, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.account = :account")
    int deleteByAccount(@Param("account") Account account);
}
