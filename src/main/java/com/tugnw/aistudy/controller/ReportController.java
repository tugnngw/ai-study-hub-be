package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import com.tugnw.aistudy.domain.dto.report.ReportResponse;
import com.tugnw.aistudy.service.ReportAdminService;
import com.tugnw.aistudy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> createReport(
            @RequestBody ReportRequest request,
            Authentication authentication) {
        UUID accountId = getCurrentUserId(authentication);
        reportService.reportDocument(request, accountId);
        return ResponseEntity.ok(ApiResponse.success("Report submitted", null));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> getReports(Pageable pageable) {
        Page<ReportResponse> reports = reportAdminService.getReports(pageable);
        return ResponseEntity.ok(ApiResponse.success("Reports retrieved", reports));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> getMyReports(
            Authentication authentication,
            Pageable pageable) {
        UUID accountId = getCurrentUserId(authentication);
        Page<ReportResponse> reports = reportAdminService.getReportsByReporter(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success("My reports retrieved", reports));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> handleReportDecision(
            @PathVariable Long id,
            @RequestBody com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest decision,
            Authentication authentication) {
        UUID adminId = getCurrentUserId(authentication);
        reportService.handleReportDecision(id, decision, adminId);
        return ResponseEntity.ok(ApiResponse.success("Report decision processed", null));
    }

    private UUID getCurrentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }
        throw new RuntimeException("Unauthorized");
    }
}