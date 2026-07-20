package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.AccountToken;
import com.tugnw.aistudy.domain.enums.AccountTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    Optional<AccountToken> findByTokenAndType(String token, AccountTokenType type);

    /**
     * Load token with a pessimistic write lock for safe concurrent consumption.
     * Blocks until any prior transaction holding the lock on this row commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountToken t WHERE t.token = :token AND t.type = :type")
    Optional<AccountToken> findByTokenAndTypeWithWriteLock(
            @Param("token") String token,
            @Param("type") AccountTokenType type);

    /**
     * Load token with a pessimistic read lock — ensures we observe the latest
     * committed state without blocking other readers.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT t FROM AccountToken t WHERE t.token = :token AND t.type = :type")
    Optional<AccountToken> findByTokenAndTypeWithReadLock(
            @Param("token") String token,
            @Param("type") AccountTokenType type);

    /**
     * Find the most recent active (unused, unexpired) token of a given type
     * for an account, newest first.
     */
    @Query("""
            SELECT t FROM AccountToken t
            WHERE t.account = :account
              AND t.type = :type
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<AccountToken> findActiveByAccountAndType(
            @Param("account") Account account,
            @Param("type") AccountTokenType type,
            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.account = :account AND t.type = :type")
    int deleteByAccountAndType(@Param("account") Account account, @Param("type") AccountTokenType type);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
