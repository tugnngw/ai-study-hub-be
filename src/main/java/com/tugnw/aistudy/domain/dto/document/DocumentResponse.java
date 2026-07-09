package com.tugnw.aistudy.domain.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Schema(description = "Document information")
public class DocumentResponse {

    @Schema(description = "Document ID", example = "a1b2c3d4-...")
    private UUID id;

    @Schema(description = "Owner user ID", example = "d7ff12cf-...")
    private UUID ownerId;

    @Schema(description = "Folder this document belongs to", example = "d7ff12cf-2ad0-4888-a9a1-b12de5d2bc9e")
    private UUID folderId;

    @Schema(description = "Subject ID", example = "1")
    private Long subjectId;

    @Schema(description = "Document title", example = "Java Concurrency Notes")
    private String title;

    @Schema(description = "Document description")
    private String description;

    @Schema(description = "AI-generated summary (markdown)")
    private String summary;

    @Schema(description = "Document processing status", example = "COMPLETED")
    private String status;

    @Schema(description = "MIME type", example = "application/pdf")
    private String mimeType;

    @Schema(description = "File size in bytes", example = "204800")
    private Long fileSize;

    @Schema(description = "Cloudinary URL")
    private String cloudinaryUrl;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Soft-delete timestamp")
    private LocalDateTime deletedAt;
}
