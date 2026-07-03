package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.ActivityLog;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.repository.ActivityLogRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Async
    @Override
    @Transactional
    public void logActivity(UUID userId, String userName, ActivityType actionType, String description) {
        logActivity(userId, userName, actionType, description, null);
    }

    @Async
    @Override
    @Transactional
    public void logActivity(UUID userId, String userName, ActivityType actionType, String description, String metadata) {
        try {
            ActivityLog log = ActivityLog.builder()
                    .userId(userId)
                    .userName(userName)
                    .actionType(actionType)
                    .description(description)
                    .metadata(metadata)
                    .build();
            activityLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage(), e);
        }
    }
}
