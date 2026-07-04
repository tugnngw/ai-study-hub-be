package com.tugnw.aistudy.domain.dto.share;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShareResponse(
        Long id,
        UUID folderId,
        UUID documentId,
        UUID ownerId,
        String ownerUsername,
        String ownerEmail,
        UUID sharedAccountId,
        String sharedUsername,
        String sharedEmail,
        String visibility,
        String shareToken,
        String shareLink,
        Instant createdAt,
        List<ShareRecipient> recipients,
        String documentTitle,
        String folderName,
        String cloudinaryUrl,
        String documentStatus
) {
    public record ShareRecipient(
            UUID accountId,
            String email,
            String username,
            String fullName
    ) {}
}
