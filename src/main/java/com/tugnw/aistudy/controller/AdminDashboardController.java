package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.admin.ActivityResponse;
import com.tugnw.aistudy.domain.dto.admin.DashboardStatsResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsResponse> getStats() {
        return ApiResponse.success("Dashboard stats", adminDashboardService.getDashboardStats());
    }

    @GetMapping("/activity")
    public ApiResponse<List<ActivityResponse>> getActivity(@RequestParam(value = "limit", defaultValue = "15") int limit) {
        List<ActivityResponse> activities = adminDashboardService.getRecentActivities(limit);
        return ApiResponse.success("Recent activities", activities);
    }

    @GetMapping("/revenue")
    public ApiResponse<com.tugnw.aistudy.domain.dto.payment.RevenueStatsResponse> getRevenueStats() {
        return ApiResponse.success("Revenue stats", adminDashboardService.getRevenueStats());
    }
}
