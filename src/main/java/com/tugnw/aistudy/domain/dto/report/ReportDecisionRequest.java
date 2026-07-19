package com.tugnw.aistudy.domain.dto.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDecisionRequest {
    private String decision; // "approved", "rejected", "removed"
    private String comment;
}