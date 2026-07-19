package com.tugnw.aistudy.domain.dto.rag;

import lombok.Builder;
import lombok.Data;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class RagChatResponse {
    private UUID sessionId;
    private String answer;
    private Set<UUID> referencedDocumentIds;
}
