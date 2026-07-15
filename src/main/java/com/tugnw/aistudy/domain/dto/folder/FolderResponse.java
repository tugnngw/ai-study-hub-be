package com.tugnw.aistudy.domain.dto.folder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Schema(description = "Folder information")
public class FolderResponse {

    @Schema(description = "Folder ID", example = "d7ff12cf-2ad0-4888-a9a1-b12de5d2bc9e")
    private UUID id;

    @Schema(description = "Folder name", example = "Chapter 1: Introduction")
    private String name;

    @Schema(description = "Folder description")
    private String description;

    @Schema(description = "AI-generated summary of folder contents")
    private String aiSummary;

    @Schema(description = "Subject this folder belongs to", example = "b2c3d4e5-...")
    private UUID subjectId;

    @Schema(description = "Semester of this folder (via subject)", example = "a1b2c3d4-...")
    private UUID semesterId;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Number of documents in this folder", example = "3")
    private int documentCount;
}
