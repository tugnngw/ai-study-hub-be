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
    private Long usersLastWeek;
    private Long usersPrevWeek;
    private Long totalDocs;
    private Long docsLastWeek;
    private Long docsPrevWeek;
    private Long totalDownloads;
    private Long downloadsLastWeek;
    private Long downloadsPrevWeek;
}
