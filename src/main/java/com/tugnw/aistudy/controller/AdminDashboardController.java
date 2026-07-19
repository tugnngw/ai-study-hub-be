package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.admin.ActivityResponse;
import com.tugnw.aistudy.domain.dto.admin.DashboardStatsResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        DashboardStatsResponse stats = adminDashboardService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", stats));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> getActivity(
            @RequestParam(value = "limit", defaultValue = "15") int limit) {
        List<ActivityResponse> activities = adminDashboardService.getRecentActivities(limit);
        return ResponseEntity.ok(ApiResponse.success("Recent activities", activities));
    }
}
