package com.tugnw.aistudy.domain.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Result of Save to My Folder operation")
public record SaveToFolderResponse(

        @Schema(description = "Total documents in shared folder", example = "6")
        int total,

        @Schema(description = "Number of documents successfully copied", example = "4")
        int copied,

        @Schema(description = "Number of documents skipped (already exist)", example = "2")
        int skipped,

        @Schema(description = "Number of documents that failed to copy", example = "0")
        int failed,

        @Schema(description = "Documents that were copied")
        List<DocumentResult> copiedDocuments,

        @Schema(description = "Documents that were skipped (already exist)")
        List<DocumentResult> skippedDocuments,

        @Schema(description = "Documents that failed to copy")
        List<DocumentResult> failedDocuments,

        @Schema(description = "Human-readable summary", example = "4 documents copied successfully. 2 documents were skipped because they already exist.")
        String message
) {
    public record DocumentResult(
            @Schema(description = "Document title", example = "Chapter1.pdf")
            String name,

            @Schema(description = "Copied document ID (null if skipped/failed)")
            UUID documentId,

            @Schema(description = "Why this document was skipped or failed", example = "Already exists")
            String reason
    ) {}
}
