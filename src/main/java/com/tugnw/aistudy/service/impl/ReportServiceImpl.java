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

        // Check if document has existing approved reports, if so, reject them
        String checkApprovedSql = "SELECT COUNT(*) FROM report WHERE document_id = ? AND status = 'approved'";
        Integer approvedCount = jdbcTemplate.queryForObject(checkApprovedSql, Integer.class, request.getDocumentId());
        
        // Update existing approved reports to rejected when new report comes in
        if (approvedCount != null && approvedCount > 0) {
            String updateApprovedSql = "UPDATE report SET status = 'rejected' WHERE document_id = ? AND status = 'approved'";
            jdbcTemplate.update(updateApprovedSql, request.getDocumentId());
        }
        
        String sql = "INSERT INTO report (document_id, reporter_id, reason, status) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, request.getDocumentId(), reporterId, request.getReason(), "pending");
    }

    @Override
    public void handleReportDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId) {
        String checkSql = "SELECT COUNT(*) FROM report WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, reportId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Report not found");
        }

        String updateSql = "UPDATE report SET status = ? WHERE id = ?";
        String newStatus = decision.getDecision();
        jdbcTemplate.update(updateSql, newStatus, reportId);

        // If approved, document should be rejected (changed from READY to REJECT)
        if ("accepted".equals(newStatus) || "approved".equals(newStatus)) {
            // Find the document id from this report
            String docIdSql = "SELECT document_id FROM report WHERE id = ?";
            UUID docId = jdbcTemplate.queryForObject(docIdSql, UUID.class, reportId);
            
            if (docId != null) {
                // Update document status to REJECT
                String updateDocSql = "UPDATE document SET status = 'REJECT' WHERE id = ? AND status = 'READY'";
                jdbcTemplate.update(updateDocSql, docId);
            }
        }
    }
}