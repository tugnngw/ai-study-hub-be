package com.tugnw.aistudy.domain.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private UUID id;
    private UUID documentId;
    private String documentTitle;
    private UUID reporterId;
    private String reporterUsername;
    private String reason;
    private String status;
    private String adminComment;
    private String cloudinaryUrl;
    private String mimeType;
    private LocalDateTime createdAt;
}
