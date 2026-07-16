package com.tugnw.aistudy.domain.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Schema(description = "A single chat message")
public class ChatMessageResponse {

    @Schema(description = "Message ID")
    private UUID id;

    @Schema(description = "USER or AI")
    private String senderType;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Referenced document IDs (JSON array)")
    private String referencedChunks;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}
