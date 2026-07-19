package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.enums.ActivityType;

import java.util.UUID;

public interface ActivityLogService {
    void logActivity(UUID userId, String userName, ActivityType actionType, String description);
    void logActivity(UUID userId, String userName, ActivityType actionType, String description, String metadata);
}
