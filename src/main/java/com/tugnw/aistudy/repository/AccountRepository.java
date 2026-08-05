package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByDeletedAtIsNotNull();
    Account findByUsername(String username);
    Account findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<Account> findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider authProvider, String providerId);
    Optional<Account> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);
    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    
    Page<Account> findAll(Pageable pageable);
    Page<Account> findByRoleAndDeletedAtIsNull(AccountRole role, Pageable pageable);
    Page<Account> findByStatusAndDeletedAtIsNull(AccountStatus status, Pageable pageable);

    long countByCreatedAtAfter(Instant date);

    /** Lock Account ngay từ SELECT — serialize mọi reserve/update trên cùng account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findAccountForUpdate(@Param("id") UUID id);
}
