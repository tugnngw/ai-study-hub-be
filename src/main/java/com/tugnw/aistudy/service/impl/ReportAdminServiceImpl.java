package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.report.ReportResponse;
import com.tugnw.aistudy.service.ReportAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportAdminServiceImpl implements ReportAdminService {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ReportResponse> rowMapper = (rs, rowNum) -> new ReportResponse(
        rs.getLong("id"),
        UUID.fromString(rs.getString("document_id")),
        rs.getString("document_title"),
        rs.getString("reporter_id") != null ? UUID.fromString(rs.getString("reporter_id")) : null,
        rs.getString("reporter_username"),
        rs.getString("reason"),
        rs.getString("status"),
        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
    );

    @Override
    public Page<ReportResponse> getReports(Pageable pageable) {
        String countSql = "SELECT COUNT(*) FROM report";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class);
        
        String dataSql = "SELECT r.id, r.document_id, d.title as document_title, r.reporter_id, a.username as reporter_username, r.reason, r.status, r.created_at " +
                "FROM report r " +
                "LEFT JOIN account a ON r.reporter_id = a.id " +
                "LEFT JOIN document d ON r.document_id = d.id " +
                "ORDER BY r.created_at DESC " +
                "LIMIT ? OFFSET ?";
        
        List<ReportResponse> reports = jdbcTemplate.query(
            dataSql,
            new Object[]{pageable.getPageSize(), pageable.getOffset()},
            rowMapper
        );
        
        return new PageImpl<>(reports, pageable, total != null ? total : 0);
    }

    @Override
    public Page<ReportResponse> getReportsByReporter(UUID reporterId, Pageable pageable) {
        String countSql = "SELECT COUNT(*) FROM report WHERE reporter_id = ?";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, reporterId);
        
        String dataSql = "SELECT r.id, r.document_id, d.title as document_title, r.reporter_id, a.username as reporter_username, r.reason, r.status, r.created_at " +
                "FROM report r " +
                "LEFT JOIN account a ON r.reporter_id = a.id " +
                "LEFT JOIN document d ON r.document_id = d.id " +
                "WHERE r.reporter_id = ? " +
                "ORDER BY r.created_at DESC " +
                "LIMIT ? OFFSET ?";
        
        List<ReportResponse> reports = jdbcTemplate.query(
            dataSql,
            new Object[]{reporterId, pageable.getPageSize(), pageable.getOffset()},
            rowMapper
        );
        
        return new PageImpl<>(reports, pageable, total != null ? total : 0);
    }
}
