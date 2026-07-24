package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quota.QuotaDetails;

import java.util.UUID;

public interface QuotaService {
    boolean checkQuota(UUID accountId, String featureType);
    boolean checkQuotaForGeneration(UUID accountId, String featureType, int quantity);
    int getRemainingQuota(UUID accountId, String featureType);
    QuotaDetails getQuotaDetails(UUID accountId);
}