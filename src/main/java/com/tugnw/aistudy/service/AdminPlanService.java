package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.plan.CreatePlanRequest;
import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.dto.plan.UpdatePlanRequest;

import java.util.List;
import java.util.UUID;

public interface AdminPlanService {
    List<PlanResponse> getAllPlans();

    PlanResponse createPlan(CreatePlanRequest request);

    PlanResponse updatePlan(UUID id, UpdatePlanRequest request);

    void hidePlan(UUID id);

    void restorePlan(UUID id);

    PlanResponse getPlanById(UUID id);

    PlanResponse setPopular(UUID id);
}
