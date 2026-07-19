package com.tugnw.aistudy.domain.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Request to update a folder")
public class FolderUpdateRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    @Schema(description = "Folder name", example = "Chapter 1: Updated")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    @Schema(description = "Folder description")
    private String description;

    @Schema(description = "Subject ID to reassign", example = "b2c3d4e5-...")
    private UUID subjectId;
}
