package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.plan.CreatePlanRequest;
import com.tugnw.aistudy.domain.dto.plan.PlanResponse;
import com.tugnw.aistudy.domain.dto.plan.UpdatePlanRequest;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.Plan;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.domain.mapper.PaymentPlanMapper;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.AdminPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlanServiceImpl implements AdminPlanService {

    private final PaymentPlanRepository paymentPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PaymentPlanMapper paymentPlanMapper;

    private List<String> deserializeFeatures(String featuresJson) {
        if (featuresJson == null)
            return null;
        try {
            return objectMapper.readValue(featuresJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private long countActiveSubscriptions(UUID planId) {
        return subscriptionRepository.countByPlan_IdAndStatus(planId, SubscriptionStatus.ACTIVE);
    }

    private String serializeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public List<PlanResponse> getAllPlans() {
        return paymentPlanRepository.findAll().stream()
                .map(plan -> {
                    PlanResponse response = paymentPlanMapper.toResponse(plan);
                    response.setFeatures(deserializeFeatures(plan.getFeatures()));
                    response.setActiveSubscriptionCount(countActiveSubscriptions(plan.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request) {
        if (paymentPlanRepository.existsByName(request.getName()))
            throw new IllegalArgumentException("Plan name already exists: " + request.getName());
        if (request.getTier() != null && paymentPlanRepository.existsByTier(request.getTier()))
            throw new IllegalArgumentException("Tier already in use by another plan: " + request.getTier());

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
                .chatLimit(request.getChatLimit() != null ? request.getChatLimit() : 0)
                .tier(request.getTier() != null ? request.getTier() : 0)
                .isActive(true)
                .build();
        PaymentPlan saved = paymentPlanRepository.save(plan);
        PlanResponse response = paymentPlanMapper.toResponse(saved);
        response.setFeatures(deserializeFeatures(saved.getFeatures()));
        response.setActiveSubscriptionCount(countActiveSubscriptions(saved.getId()));
        return response;
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));

        boolean isFree = Plan.FREE.name().equalsIgnoreCase(plan.getName());
        long activeCount = isFree ? 0 : subscriptionRepository.countByPlan_IdAndStatus(id, SubscriptionStatus.ACTIVE);

        if (!isFree && activeCount > 0) {

            plan.setIsActive(false);
            paymentPlanRepository.save(plan);
            paymentPlanRepository.flush();

            String newName = generateUniquePlanName(plan.getName());

            PaymentPlan newPlan = PaymentPlan.builder()
                    .name(newName)
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
                    .chatLimit(request.getChatLimit() != null ? request.getChatLimit() : plan.getChatLimit())
                    .tier(request.getTier() != null ? request.getTier() : plan.getTier())
                    .isActive(true)
                    .build();

            PaymentPlan saved = paymentPlanRepository.save(newPlan);
            PlanResponse response = paymentPlanMapper.toResponse(saved);
            response.setFeatures(deserializeFeatures(saved.getFeatures()));
            response.setActiveSubscriptionCount(0L);
            return response;
        }

        if (request.getName() != null && !request.getName().equals(plan.getName())) {
            if (paymentPlanRepository.existsByName(request.getName()))
                throw new IllegalArgumentException("Plan name already exists: " + request.getName());
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
        if (request.getChatLimit() != null) plan.setChatLimit(request.getChatLimit());
        if (request.getTier() != null) {
            Optional<PaymentPlan> existingTier = paymentPlanRepository.findByTier(request.getTier());
            if (existingTier.isPresent() && !existingTier.get().getId().equals(plan.getId()))
                throw new IllegalArgumentException("Tier already in use by plan: " + existingTier.get().getName());
            plan.setTier(request.getTier());
        }
        if (request.getIsActive() != null) plan.setIsActive(request.getIsActive());

        PaymentPlan saved = paymentPlanRepository.save(plan);
        PlanResponse response = paymentPlanMapper.toResponse(saved);
        response.setFeatures(deserializeFeatures(saved.getFeatures()));
        response.setActiveSubscriptionCount(countActiveSubscriptions(saved.getId()));
        return response;
    }

    @Override
    @Transactional
    public void hidePlan(UUID id) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        plan.setIsActive(false);
        paymentPlanRepository.save(plan);
    }

    @Override
    @Transactional
    public void restorePlan(UUID id) {
        PaymentPlan plan = paymentPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        plan.setIsActive(true);
        paymentPlanRepository.save(plan);
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
            }
        }

        // Mark this plan as popular
        plan.setIsPopular(true);
        PaymentPlan saved = paymentPlanRepository.save(plan);

        PlanResponse response = paymentPlanMapper.toResponse(saved);
        response.setFeatures(deserializeFeatures(saved.getFeatures()));
        response.setActiveSubscriptionCount(countActiveSubscriptions(saved.getId()));
        return response;
    }

    @Override
    public PlanResponse getPlanById(UUID id) {
        return paymentPlanRepository.findById(id)
                .map(plan -> {
                    PlanResponse response = paymentPlanMapper.toResponse(plan);
                    response.setFeatures(deserializeFeatures(plan.getFeatures()));
                    response.setActiveSubscriptionCount(countActiveSubscriptions(plan.getId()));
                    return response;
                })
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
    }

    private String generateUniquePlanName(String baseName) {
        String newName = baseName;
        int version = 2;
        while (paymentPlanRepository.existsByName(newName)) {
            newName = baseName + " v" + version;
            version++;
        }
        return newName;
    }
}