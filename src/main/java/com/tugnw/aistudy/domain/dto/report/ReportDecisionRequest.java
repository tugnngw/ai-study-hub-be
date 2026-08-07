package com.tugnw.aistudy.domain.dto.report;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDecisionRequest {
    private String decision; // "approved", "rejected", "removed"

    @Size(max = 500, message = "Comment must be at most 500 characters")
    private String comment;
}
