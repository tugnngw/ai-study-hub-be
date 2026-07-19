package com.tugnw.aistudy.domain.dto.report;

import lombok.Data;
import java.util.UUID;

@Data
public class ReportRequest {
    private UUID documentId;
    private String reason;
    private String description;
}