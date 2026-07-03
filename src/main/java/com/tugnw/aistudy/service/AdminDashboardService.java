package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.admin.ActivityResponse;
import com.tugnw.aistudy.domain.dto.admin.DashboardStatsResponse;

import java.util.List;

public interface AdminDashboardService {
    DashboardStatsResponse getDashboardStats();
    List<ActivityResponse> getRecentActivities(int limit);
}
