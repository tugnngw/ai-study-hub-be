package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import java.util.UUID;

public interface ReportService {
    void reportDocument(ReportRequest request, UUID accountId);
    void handleReportDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId);

    /** Kháng cáo doc bị BANNED — chỉ owner, doc BANNED, chưa có appeal pending. */
    void submitAppeal(ReportRequest request, UUID accountId);
    /** Xử lý appeal: approved → doc READY; rejected → doc giữ BANNED. */
    void handleAppealDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId);
    /** Đọc type của report (REPORT/APPEAL) — cho controller route. */
    String findReportType(UUID reportId);
}