package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.service.AdminPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Tag(name = "Public Plans", description = "Public endpoints for viewing payment plans")
public class PlanPublicController {

    private final AdminPlanService adminPlanService;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getActivePlans() {
        List<PlanResponse> plans = adminPlanService.getAllPlans().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && !"Free".equalsIgnoreCase(p.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(plans);
    }
}
