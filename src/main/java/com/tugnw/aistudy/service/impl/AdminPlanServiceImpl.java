package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.plan.CreatePlanRequest;
import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.dto.plan.UpdatePlanRequest;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.AdminPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlanServiceImpl implements AdminPlanService {

    private final PaymentPlanRepository paymentPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    private PlanResponse mapToPlanResponse(PaymentPlan plan) {
        List<String> features = null;
        if (plan.getFeatures() != null) {
            try {
                features = objectMapper.readValue(plan.getFeatures(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse features JSON for plan {}: {}", plan.getId(), e.getMessage());
            }
        }
        long activeCount = subscriptionRepository.countByPlan_IdAndStatus(plan.getId(), SubscriptionStatus.ACTIVE);
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .tagline(plan.getTagline())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .storageGb(plan.getStorageGb())
                .aiQuestions(plan.getAiQuestions())
                .features(features)
                .isPopular(plan.getIsPopular())
                .displayOrder(plan.getDisplayOrder())
                .isActive(plan.getIsActive())
                .activeSubscriptionCount(activeCount)
                .flashcardLimit(plan.getFlashcardLimit())
                .questionLimit(plan.getQuestionLimit())
                .summaryLimit(plan.getSummaryLimit())
                .build();
    }

    private String serializeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize features: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<PlanResponse> getAllPlans() {
        return paymentPlanRepository.findAll().stream()
                .map(this::mapToPlanResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request) {
        if (paymentPlanRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Plan name already exists: " + request.getName());
        }
        PaymentPlan plan = PaymentPlan.builder()
                .name(request.getName())
                .tagline(request.getTagline())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .storageGb(request.getStorageGb())
                .aiQuestions(request.getAiQuestions())
                .features(serializeFeatures(request.getFeatures()))
                .isPopular(request.getIsPopular() != null ? request.getIsPopular() : false)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .flashcardLimit(request.getFlashcardLimit() != null ? request.getFlashcardLimit() : 0)
                .questionLimit(request.getQuestionLimit() != null ? request.getQuestionLimit() : 0)
                .summaryLimit(request.getSummaryLimit() != null ? request.getSummaryLimit() : 0)
                .isActive(true)
                .build();
        PaymentPlan saved = paymentPlanRepository.save(plan);
        log.info("Created new plan: {}", saved.getName());
        return mapToPlanResponse(saved);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));

        boolean isFree = "Free".equalsIgnoreCase(plan.getName());
        long activeCount = isFree ? 0 : subscriptionRepository.countByPlan_IdAndStatus(id, SubscriptionStatus.ACTIVE);

        if (!isFree && activeCount > 0) {
            log.info("Plan {} has active subscriptions, creating new version for update.", plan.getName());
            
            // Ẩn gói cũ
            plan.setIsActive(false);
            paymentPlanRepository.save(plan);

            // Tạo gói mới dựa trên thông tin cập nhật
            PaymentPlan newPlan = PaymentPlan.builder()
                    .name(request.getName() != null ? request.getName() : plan.getName() + " (v2)")
                    .tagline(request.getTagline() != null ? request.getTagline() : plan.getTagline())
                    .description(request.getDescription() != null ? request.getDescription() : plan.getDescription())
                    .price(request.getPrice() != null ? request.getPrice() : plan.getPrice())
                    .durationDays(request.getDurationDays() != null ? request.getDurationDays() : plan.getDurationDays())
                    .storageGb(request.getStorageGb() != null ? request.getStorageGb() : plan.getStorageGb())
                    .aiQuestions(request.getAiQuestions() != null ? request.getAiQuestions() : plan.getAiQuestions())
                    .features(request.getFeatures() != null ? serializeFeatures(request.getFeatures()) : plan.getFeatures())
                    .isPopular(request.getIsPopular() != null ? request.getIsPopular() : plan.getIsPopular())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : plan.getDisplayOrder())
                .flashcardLimit(request.getFlashcardLimit() != null ? request.getFlashcardLimit() : plan.getFlashcardLimit())
                .questionLimit(request.getQuestionLimit() != null ? request.getQuestionLimit() : plan.getQuestionLimit())
                .summaryLimit(request.getSummaryLimit() != null ? request.getSummaryLimit() : plan.getSummaryLimit())
                .isActive(true)
                .build();
            
            PaymentPlan saved = paymentPlanRepository.save(newPlan);
            log.info("Created new version of plan: {}", saved.getName());
            return mapToPlanResponse(saved);
        }

        // Cập nhật trực tiếp cho gói Free hoặc các gói không có người dùng
        if (request.getName() != null && !request.getName().equals(plan.getName())) {
            if (paymentPlanRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("Plan name already exists: " + request.getName());
            }
            plan.setName(request.getName());
        }
        if (request.getTagline() != null) plan.setTagline(request.getTagline());
        if (request.getDescription() != null) plan.setDescription(request.getDescription());
        if (request.getPrice() != null) plan.setPrice(request.getPrice());
        if (request.getDurationDays() != null) plan.setDurationDays(request.getDurationDays());
        if (request.getStorageGb() != null) plan.setStorageGb(request.getStorageGb());
        if (request.getAiQuestions() != null) plan.setAiQuestions(request.getAiQuestions());
        if (request.getFeatures() != null) plan.setFeatures(serializeFeatures(request.getFeatures()));
        if (request.getIsPopular() != null) plan.setIsPopular(request.getIsPopular());
        if (request.getDisplayOrder() != null) plan.setDisplayOrder(request.getDisplayOrder());
        if (request.getFlashcardLimit() != null) plan.setFlashcardLimit(request.getFlashcardLimit());
        if (request.getQuestionLimit() != null) plan.setQuestionLimit(request.getQuestionLimit());
        if (request.getSummaryLimit() != null) plan.setSummaryLimit(request.getSummaryLimit());
        if (request.getIsActive() != null) plan.setIsActive(request.getIsActive());

        PaymentPlan saved = paymentPlanRepository.save(plan);
        log.info("Updated plan: {}", saved.getName());
        return mapToPlanResponse(saved);
    }

    @Override
    @Transactional
    public void hidePlan(UUID id) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));

        long activeCount = subscriptionRepository.countByPlan_IdAndStatus(id, SubscriptionStatus.ACTIVE);
        if (activeCount > 0) {
            log.warn("Hiding plan {} with {} active subscriptions", plan.getName(), activeCount);
        }
        plan.setIsActive(false);
        paymentPlanRepository.save(plan);
        log.info("Hidden plan: {}", plan.getName());
    }

    @Override
    @Transactional
    public void restorePlan(UUID id) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        plan.setIsActive(true);
        paymentPlanRepository.save(plan);
        log.info("Restored plan: {}", plan.getName());
    }

    @Override
    @Transactional
    public PlanResponse setPopular(UUID id) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        
        // Unmark other popular plans
        List<PaymentPlan> allPlans = paymentPlanRepository.findAll();
        for (PaymentPlan otherPlan : allPlans) {
            if (otherPlan.getIsPopular() && !otherPlan.getId().equals(id)) {
                otherPlan.setIsPopular(false);
                paymentPlanRepository.save(otherPlan);
                log.info("Unmarked plan {} as popular", otherPlan.getName());
            }
        }
        
        // Mark this plan as popular
        plan.setIsPopular(true);
        PaymentPlan saved = paymentPlanRepository.save(plan);
        log.info("Marked plan {} as popular", saved.getName());
        
        return mapToPlanResponse(saved);
    }

    @Override
    public PlanResponse getPlanById(UUID id) {
        return paymentPlanRepository.findById(id)
                .map(this::mapToPlanResponse)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
    }
}
