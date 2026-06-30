package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.report.ReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportAdminService {
    Page<ReportResponse> getReports(Pageable pageable);
    Page<ReportResponse> getReportsByReporter(UUID reporterId, Pageable pageable);
}
