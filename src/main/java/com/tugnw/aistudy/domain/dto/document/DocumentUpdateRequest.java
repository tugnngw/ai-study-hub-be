package com.tugnw.aistudy.domain.dto.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Request to update a document")
public class DocumentUpdateRequest {

    @Size(max = 255, message = "Title must be at most 255 characters")
    @Schema(description = "Document title", example = "Updated Java Concurrency Notes")
    private String title;

    @Schema(description = "Document description")
    private String description;

    @Schema(description = "Folder ID to reassign")
    private UUID folderId;
}
