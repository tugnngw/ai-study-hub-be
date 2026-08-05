package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import com.tugnw.aistudy.domain.dto.report.ReportResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.ReportAdminService;
import com.tugnw.aistudy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;
    private final ReportAdminService reportAdminService;

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> createReport(
            @RequestBody ReportRequest request,
            Authentication authentication) {
        reportService.reportDocument(request, userId(authentication));
        return ApiResponse.success("Report submitted", null);
    }

    @PostMapping("/appeal")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> submitAppeal(
            @RequestBody ReportRequest request,
            Authentication authentication) {
        reportService.submitAppeal(request, userId(authentication));
        return ApiResponse.success("Appeal submitted", null);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<ReportResponse>> getReports(
            @RequestParam(required = false) String type,
            Pageable pageable) {
        Page<ReportResponse> reports = type != null && !type.isBlank()
            ? reportAdminService.getReportsByType(type.toUpperCase(), pageable)
            : reportAdminService.getReports(pageable);
        return ApiResponse.success("Reports retrieved", reports);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<ReportResponse>> getMyReports(
            Authentication authentication,
            Pageable pageable) {
        Page<ReportResponse> reports = reportAdminService.getReportsByReporter(userId(authentication), pageable);
        return ApiResponse.success("My reports retrieved", reports);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<ReportResponse>> getAllReports(Pageable pageable) {
        Page<ReportResponse> reports = reportAdminService.getAllReports(pageable);
        return ApiResponse.success("All reports retrieved", reports);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> handleReportDecision(
            @PathVariable UUID id,
            @RequestBody com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest decision,
            Authentication authentication) {
        // Routing theo report.type — KHÔNG phải business. Service tách 2 nghiệp vụ riêng.
        String type = reportService.findReportType(id);
        if ("APPEAL".equals(type)) {
            reportService.handleAppealDecision(id, decision, userId(authentication));
        } else {
            reportService.handleReportDecision(id, decision, userId(authentication));
        }
        return ApiResponse.success("Report decision processed", null);
    }
}