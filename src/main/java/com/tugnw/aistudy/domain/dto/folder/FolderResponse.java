package com.tugnw.aistudy.domain.dto.folder;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class FolderResponse {

    private UUID id;
    private String name;
    private String aiSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int documentCount; // số tài liệu trong folder
}
