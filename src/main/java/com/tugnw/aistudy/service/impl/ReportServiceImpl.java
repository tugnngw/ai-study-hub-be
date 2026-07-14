package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import com.tugnw.aistudy.domain.entity.Document;
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
        Document doc = documentRepository.findByIdAndDeletedAtIsNull(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Cập nhật trạng thái document thành REPORTED
        doc.setStatus(com.tugnw.aistudy.domain.enums.DocumentStatus.REPORTED.name());
        documentRepository.save(doc);

        // Check if document has existing approved reports, if so, reject them
        String checkApprovedSql = "SELECT COUNT(*) FROM report WHERE document_id = ? AND status = 'approved'";
        Integer approvedCount = jdbcTemplate.queryForObject(checkApprovedSql, Integer.class, request.getDocumentId());
        
        // Update existing approved reports to rejected when new report comes in
        if (approvedCount != null && approvedCount > 0) {
            String updateApprovedSql = "UPDATE report SET status = 'rejected' WHERE document_id = ? AND status = 'approved'";
            jdbcTemplate.update(updateApprovedSql, request.getDocumentId());
        }
        
        String sql = "INSERT INTO report (document_id, reporter_id, reason, status) VALUES (?, ?, ?, 'pending')";
        jdbcTemplate.update(sql, request.getDocumentId(), reporterId, request.getReason());
    }

    @Override
    public void handleReportDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId) {
        String checkSql = "SELECT COUNT(*) FROM report WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, reportId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Report not found");
        }

        String updateSql = "UPDATE report SET status = ?, admin_comment = ? WHERE id = ?";
        String newStatus = decision.getDecision();
        String comment = decision.getComment();
        jdbcTemplate.update(updateSql, newStatus, comment, reportId);

        // If approved, document should be BANNED (changed from READY to BANNED)
        if ("accepted".equals(newStatus) || "approved".equals(newStatus)) {
            // Find the document id from this report
            String docIdSql = "SELECT document_id FROM report WHERE id = ?";
            UUID docId = jdbcTemplate.queryForObject(docIdSql, UUID.class, reportId);
            
            if (docId != null) {
                // Update document status to BANNED
                String updateDocSql = "UPDATE document SET status = 'BANNED' WHERE id = ?";
                jdbcTemplate.update(updateDocSql, docId);
            }
        } else if ("rejected".equals(newStatus) || "removed".equals(newStatus)) {
            // If report is rejected/removed, revert document to READY (assuming it was REPORTED)
            String docIdSql = "SELECT document_id FROM report WHERE id = ?";
            UUID docId = jdbcTemplate.queryForObject(docIdSql, UUID.class, reportId);
            
            if (docId != null) {
                // Update document status to READY
                String updateDocSql = "UPDATE document SET status = 'READY' WHERE id = ?";
                jdbcTemplate.update(updateDocSql, docId);
            }
        }
        // Always enforce BANNED status visibility: Banned documents cannot be accessed
        String enforceBanSql = "UPDATE document SET status = 'BANNED' WHERE status = 'REPORTED' AND id IN (SELECT document_id FROM report WHERE status = 'approved' OR status = 'accepted')";
        jdbcTemplate.update(enforceBanSql);
    }
}