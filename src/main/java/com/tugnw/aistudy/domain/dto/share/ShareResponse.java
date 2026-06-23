package com.tugnw.aistudy.domain.dto.share;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        Long id,
        UUID folderId,
        UUID ownerId,
        UUID sharedAccountId,
        String sharedUsername,
        String sharedEmail,
        String visibility,
        Instant createdAt
) {
}
