package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<Void>> getActivity() {
        return ResponseEntity.ok(ApiResponse.success("Activity data", null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Void>> getStats() {
        return ResponseEntity.ok(ApiResponse.success("Stats data", null));
    }
}
