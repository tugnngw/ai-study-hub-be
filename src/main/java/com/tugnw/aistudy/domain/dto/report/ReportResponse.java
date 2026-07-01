package com.tugnw.aistudy.domain.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
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
    private LocalDateTime createdAt;
}
