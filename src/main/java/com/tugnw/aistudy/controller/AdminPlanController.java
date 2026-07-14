package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.plan.CreatePlanRequest;
import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.dto.plan.UpdatePlanRequest;
import com.tugnw.aistudy.service.AdminPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        return ResponseEntity.ok(adminPlanService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlanById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminPlanService.getPlanById(id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.ok(adminPlanService.createPlan(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(adminPlanService.updatePlan(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hidePlan(@PathVariable UUID id) {
        adminPlanService.hidePlan(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restorePlan(@PathVariable UUID id) {
        adminPlanService.restorePlan(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/popular")
    public ResponseEntity<PlanResponse> setPopular(@PathVariable UUID id) {
        return ResponseEntity.ok(adminPlanService.setPopular(id));
    }
}
