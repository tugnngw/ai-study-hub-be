package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import java.util.UUID;

public interface ReportService {
    void reportDocument(ReportRequest request, UUID accountId);
    void handleReportDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId);
}