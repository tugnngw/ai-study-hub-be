package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByToken(String token);

    boolean existsByToken(String token);

    /**
     * Find the most recent unverified, non-expired token for an account,
     * ordered by creation time descending (newest first).
     */
    @Query("""
            SELECT vt FROM VerificationToken vt
            WHERE vt.account = :account
              AND vt.verifiedAt IS NULL
              AND vt.expiresAt > :now
            ORDER BY vt.createdAt DESC
            LIMIT 1
            """)
    Optional<VerificationToken> findActiveByAccount(@Param("account") Account account, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.account = :account")
    int deleteByAccount(@Param("account") Account account);

    @Modifying
    @Query("DELETE FROM VerificationToken vt WHERE vt.account.id IN :accountIds")
    int deleteByAccountIdIn(@Param("accountIds") List<UUID> accountIds);

    @Modifying
    @Query("""
            UPDATE VerificationToken vt SET vt.verifiedAt = :verifiedAt
            WHERE vt.token = :token AND vt.verifiedAt IS NULL
            """)
    int markVerified(@Param("token") String token, @Param("verifiedAt") Instant verifiedAt);
}
