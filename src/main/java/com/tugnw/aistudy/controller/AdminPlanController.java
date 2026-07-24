package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.plan.CreatePlanRequest;
import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.dto.plan.UpdatePlanRequest;
import com.tugnw.aistudy.service.AdminPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Plans", description = "Admin endpoints for managing payment plans")
public class AdminPlanController {

    private final AdminPlanService adminPlanService;

    @GetMapping
    public ApiResponse<List<PlanResponse>> getAllPlans() {
        return ApiResponse.success(adminPlanService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanResponse> getPlanById(@PathVariable UUID id) {
        return ApiResponse.success(adminPlanService.getPlanById(id));
    }

    @PostMapping
    public ApiResponse<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ApiResponse.success(adminPlanService.createPlan(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanResponse> updatePlan(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return ApiResponse.success(adminPlanService.updatePlan(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> hidePlan(@PathVariable UUID id) {
        adminPlanService.hidePlan(id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/restore")
    public ApiResponse<Void> restorePlan(@PathVariable UUID id) {
        adminPlanService.restorePlan(id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/popular")
    public ApiResponse<PlanResponse> setPopular(@PathVariable UUID id) {
        return ApiResponse.success(adminPlanService.setPopular(id));
    }
}
