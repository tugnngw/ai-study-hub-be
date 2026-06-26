package com.tugnw.aistudy.domain.dto.document;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DocumentResponse {

    private UUID id;
    private UUID ownerId;
    private UUID folderId;
    private Long subjectId;
    private String title;
    private String description;
    private String summary;
    private String status;
    private String mimeType;
    private Long fileSize;
    private String cloudinaryUrl;
    private LocalDateTime createdAt;
}