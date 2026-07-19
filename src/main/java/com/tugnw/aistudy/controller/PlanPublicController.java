package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.service.PaymentService;
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

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<PaymentPlan>> getActivePlans() {
        List<PaymentPlan> plans = paymentService.listActivePlans().stream()
                .filter(p -> !"Free".equalsIgnoreCase(p.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(plans);
    }
}
