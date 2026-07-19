package com.tugnw.aistudy.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Account owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_account_id")
    private Account sharedAccount;

    @Column(name = "share_token", unique = true, length = 36)
    private String shareToken;

    @Column(name = "visibility", nullable = false, length = 50)
    @Builder.Default
    private String visibility = "private";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.shareToken == null) {
            this.shareToken = UUID.randomUUID().toString();
        }
    }
}
