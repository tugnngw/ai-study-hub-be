package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Account findByUsername(String username);
    Account findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<Account> findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider authProvider, String providerId);
    Optional<Account> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);
}
