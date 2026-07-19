package com.tugnw.aistudy.domain.dto.document;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class DocumentUpdateRequest {

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    private String description;

    private UUID folderId;

    private Long subjectId;
}