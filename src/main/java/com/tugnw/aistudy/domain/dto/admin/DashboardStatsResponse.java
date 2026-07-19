package com.tugnw.aistudy.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private Long totalUsers;
    private Double totalUsersTrend;
    private Long totalDocs;
    private Double totalDocsTrend;
    private Long totalDownloads;
    private Double totalDownloadsTrend;
}
