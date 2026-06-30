package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportServiceImpl implements ReportService {
    private final DocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void reportDocument(ReportRequest request, UUID reporterId) {
        documentRepository.findByIdAndDeletedAtIsNull(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        String sql = "INSERT INTO report (document_id, reporter_id, reason, status) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, request.getDocumentId(), reporterId, request.getReason(), "pending");
    }

    @Override
    public void handleReportDecision(Long reportId, ReportDecisionRequest decision, UUID adminId) {
        String checkSql = "SELECT COUNT(*) FROM report WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, reportId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Report not found");
        }

        String updateSql = "UPDATE report SET status = ?, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(updateSql, decision.getDecision(), reportId);

        if ("removed".equals(decision.getDecision())) {
            String updateDocSql = "UPDATE document SET status = 'reported' WHERE id = (SELECT document_id FROM report WHERE id = ?)";
            jdbcTemplate.update(updateDocSql, reportId);
        }
    }
}