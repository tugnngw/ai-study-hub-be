package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.report.ReportDecisionRequest;
import com.tugnw.aistudy.domain.dto.report.ReportRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.DocumentStatus;
import com.tugnw.aistudy.exception.ResourceNotFoundException;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
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

    public static final String TYPE_REPORT = "REPORT";
    public static final String TYPE_APPEAL = "APPEAL";

    // ============ HELPERS (dùng chung cả REPORT lẫn APPEAL) ============

    private void insertReportRow(UUID documentId, UUID reporterId, String reason, String type) {
        String sql = "INSERT INTO report (document_id, reporter_id, reason, status, type) VALUES (?, ?, ?, 'pending', ?)";
        jdbcTemplate.update(sql, documentId, reporterId, reason, type);
    }

    @Override
    public String findReportType(UUID reportId) {
        try {
            return jdbcTemplate.queryForObject("SELECT type FROM report WHERE id = ?", String.class, reportId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }
    }

    private void updateReportDecision(UUID reportId, String newStatus, String comment) {
        String updateSql = "UPDATE report SET status = ?, admin_comment = ? WHERE id = ?";
        jdbcTemplate.update(updateSql, newStatus, comment, reportId);
    }

    private UUID findDocumentIdByReport(UUID reportId) {
        return jdbcTemplate.queryForObject("SELECT document_id FROM report WHERE id = ?", UUID.class, reportId);
    }

    private void requireReportExists(UUID reportId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report WHERE id = ?", Integer.class, reportId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Report not found");
        }
    }

    /** Báo cáo approved → document BANNED; rejected/removed → READY. */
    private void applyReportDecisionOnDocument(UUID docId, String decision) {
        String newStatus;
        if ("accepted".equals(decision) || "approved".equals(decision)) {
            newStatus = DocumentStatus.BANNED.name();
        } else {
            newStatus = DocumentStatus.READY.name();
        }
        jdbcTemplate.update("UPDATE document SET status = ? WHERE id = ?", newStatus, docId);
    }

    /** Appeal approved → document READY (clear reason); rejected/removed → giữ BANNED. */
    private void applyAppealDecisionOnDocument(UUID docId, String decision) {
        boolean approved = "accepted".equals(decision) || "approved".equals(decision);
        if (approved) {
            jdbcTemplate.update("UPDATE document SET status = ?, reject_reason = NULL WHERE id = ?",
                    DocumentStatus.READY.name(), docId);
        }
    }

    // ============ REPORT (giữ nguyên hành vi cũ) ============

    @Override
    public void reportDocument(ReportRequest request, UUID reporterId) {
        Document doc = documentRepository.findByIdAndDeletedAtIsNull(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Cập nhật trạng thái document thành REPORTED
        doc.setStatus(DocumentStatus.REPORTED.name());
        documentRepository.save(doc);

        // Check if document has existing approved reports, if so, reject them
        String checkApprovedSql = "SELECT COUNT(*) FROM report WHERE document_id = ? AND status = 'approved'";
        Integer approvedCount = jdbcTemplate.queryForObject(checkApprovedSql, Integer.class, request.getDocumentId());

        // Update existing approved reports to rejected when new report comes in
        if (approvedCount != null && approvedCount > 0) {
            String updateApprovedSql = "UPDATE report SET status = 'rejected' WHERE document_id = ? AND status = 'approved'";
            jdbcTemplate.update(updateApprovedSql, request.getDocumentId());
        }

        insertReportRow(request.getDocumentId(), reporterId, request.getReason(), TYPE_REPORT);
    }

    @Override
    public void handleReportDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId) {
        requireReportExists(reportId);
        String type = findReportType(reportId);
        if (!TYPE_REPORT.equals(type)) {
            throw new IllegalArgumentException("Report is not a REPORT");
        }

        updateReportDecision(reportId, decision.getDecision(), decision.getComment());
        UUID docId = findDocumentIdByReport(reportId);
        if (docId != null) {
            applyReportDecisionOnDocument(docId, decision.getDecision());
        }

        // Always enforce BANNED status visibility: Banned documents cannot be accessed
        String enforceBanSql = "UPDATE document SET status = 'BANNED' WHERE status = 'REPORTED' AND id IN (SELECT document_id FROM report WHERE status = 'approved' OR status = 'accepted')";
        jdbcTemplate.update(enforceBanSql);
    }

    // ============ APPEAL ============

    @Override
    public void submitAppeal(ReportRequest request, UUID accountId) {
        Document doc = documentRepository.findByIdAndDeletedAtIsNull(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        // Chỉ owner của document được kháng cáo
        if (!doc.getOwnerId().equals(accountId)) {
            throw new IllegalArgumentException("Only the document owner can appeal");
        }
        // Chỉ document BANNED mới kháng cáo được
        if (!DocumentStatus.BANNED.name().equalsIgnoreCase(doc.getStatus())) {
            throw new IllegalArgumentException("Document is not banned");
        }
        // Không tạo appeal thứ 2 khi đang chờ xử lý
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE document_id = ? AND type = ? AND status = 'pending'",
                Integer.class, request.getDocumentId(), TYPE_APPEAL);
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalArgumentException("Appeal already pending");
        }

        insertReportRow(request.getDocumentId(), accountId, request.getReason(), TYPE_APPEAL);
    }

    @Override
    public void handleAppealDecision(UUID reportId, ReportDecisionRequest decision, UUID adminId) {
        requireReportExists(reportId);
        String type = findReportType(reportId);
        if (!TYPE_APPEAL.equals(type)) {
            throw new IllegalArgumentException("Report is not an APPEAL");
        }

        updateReportDecision(reportId, decision.getDecision(), decision.getComment());
        UUID docId = findDocumentIdByReport(reportId);
        if (docId != null) {
            applyAppealDecisionOnDocument(docId, decision.getDecision());
        }
    }
}
