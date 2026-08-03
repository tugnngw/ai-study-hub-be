package com.tugnw.aistudy.domain.entity;

import com.tugnw.aistudy.domain.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.tugnw.aistudy.domain.enums.AccountRole;
import com.tugnw.aistudy.domain.enums.AccountStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "account")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 40)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 30)
    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String avatarUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AccountRole role = AccountRole.USER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 50)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Builder.Default
    @Column(name = "storage_gb", nullable = false)
    private Double storageGb = 1.0D;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "verification_token_verified_at")
    private Instant verificationTokenVerifiedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

}
