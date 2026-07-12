package com.tugnw.aistudy.domain.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Request to create a new folder")
public class FolderCreateRequest {

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Schema(description = "Folder name", example = "Chapter 1: Introduction", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "Subject ID must not be null")
    @Schema(description = "Subject this folder belongs to", example = "b2c3d4e5-...", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID subjectId;
}
