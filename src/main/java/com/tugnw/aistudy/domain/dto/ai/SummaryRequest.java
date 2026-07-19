package com.tugnw.aistudy.domain.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to generate AI summary for a document")
public class SummaryRequest {

    @NotNull(message = "documentId must not be null")
    @Schema(description = "Document ID to summarize", example = "a1b2c3d4-...", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID documentId;

    @Schema(description = "Force regeneration (overwrites cached summary)", example = "false", defaultValue = "false")
    private boolean force;
}
