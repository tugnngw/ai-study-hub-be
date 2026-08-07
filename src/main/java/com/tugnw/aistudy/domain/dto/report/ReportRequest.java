package com.tugnw.aistudy.domain.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class ReportRequest {
    private UUID documentId;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
}
