package com.tugnw.aistudy.domain.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Chat session with messages")
public class ChatSessionResponse {

    @Schema(description = "Session ID")
    private UUID id;

    @Schema(description = "Document ID this session belongs to")
    private UUID documentId;

    @Schema(description = "Session title (first user message)")
    private String title;

    @Schema(description = "Number of messages in this session")
    private int messageCount;

    @Schema(description = "Messages in this session")
    private List<ChatMessageResponse> messages;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp")
    private LocalDateTime updatedAt;
}
