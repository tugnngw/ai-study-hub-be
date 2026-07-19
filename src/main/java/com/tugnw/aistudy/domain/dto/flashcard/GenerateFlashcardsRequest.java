package com.tugnw.aistudy.domain.dto.flashcard;

import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class GenerateFlashcardsRequest {

    // Generation source (choose one: documentId/documentIds, or folderId)
    private UUID documentId;                    // Backward compatibility
    private List<UUID> documentIds;             // New: Multiple documents
    private UUID folderId;                      // New: Entire folder
    private boolean includeAllDocuments = false; // New: If folderId, feztch all docs

    // Generation parameter
    @Min(value = 1, message = "Number of cards must be at least 1")
    private Integer numberOfCards;
}
