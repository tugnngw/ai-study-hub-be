package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.admin.ActivityResponse;
import com.tugnw.aistudy.domain.dto.admin.DashboardStatsResponse;
import com.tugnw.aistudy.domain.entity.ActivityLog;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.ActivityLogRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AccountRepository accountRepository;
    private final DocumentRepository documentRepository;
    private final ActivityLogRepository activityLogRepository;

    @Override
    public DashboardStatsResponse getDashboardStats() {
        Instant now = Instant.now();
        Instant lastWeek = now.minus(7, ChronoUnit.DAYS);
        Instant twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);

        long totalUsers = accountRepository.count();
        long totalDocs = documentRepository.count();
        
        Long currentDownloads = activityLogRepository.countByActionTypeAndCreatedAtAfter(
                ActivityType.DOCUMENT_DOWNLOAD, lastWeek);
        Long previousDownloads = activityLogRepository.countByActionTypeAndCreatedAtAfter(
                ActivityType.DOCUMENT_DOWNLOAD, twoWeeksAgo);
        previousDownloads = previousDownloads - (currentDownloads != null ? currentDownloads : 0);

        double usersTrend = calculateTrend(totalUsers, totalUsers);
        double docsTrend = calculateTrend(totalDocs, totalDocs);
        double downloadsTrend = calculateTrend(
                currentDownloads != null ? currentDownloads : 0,
                previousDownloads != null ? previousDownloads : 0
        );

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalUsersTrend(usersTrend)
                .totalDocs(totalDocs)
                .totalDocsTrend(docsTrend)
                .totalDownloads(currentDownloads != null ? currentDownloads : 0L)
                .totalDownloadsTrend(downloadsTrend)
                .build();
    }

    @Override
    public List<ActivityResponse> getRecentActivities(int limit) {
        List<ActivityLog> logs = activityLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, limit));

        return logs.stream()
                .map(this::mapToActivityResponse)
                .collect(Collectors.toList());
    }

    private ActivityResponse mapToActivityResponse(ActivityLog log) {
        String type = mapActivityTypeToFrontend(log.getActionType());
        String title = log.getDescription();
        String actor = log.getUserName() != null ? log.getUserName() : "System";
        String time = formatRelativeTime(log.getCreatedAt());

        return ActivityResponse.builder()
                .id(log.getId().toString())
                .title(title)
                .actor(actor)
                .type(type)
                .time(time)
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String mapActivityTypeToFrontend(ActivityType activityType) {
        switch (activityType) {
            case USER_REGISTER:
            case USER_UPGRADE:
                return "user";
            case DOCUMENT_UPLOAD:
                return "upload";
            case DOCUMENT_DELETE:
                return "delete";
            case PAYMENT_FAILED:
            case DOCUMENT_DOWNLOAD:
            default:
                return "report";
        }
    }

    private String formatRelativeTime(Instant instant) {
        Duration duration = Duration.between(instant, Instant.now());
        long minutes = duration.toMinutes();
        long hours = duration.toHours();
        long days = duration.toDays();

        if (minutes < 1) {
            return "Vừa xong";
        } else if (minutes < 60) {
            return minutes + " phút trước";
        } else if (hours < 24) {
            return hours + " giờ trước";
        } else if (days < 7) {
            return days + " ngày trước";
        } else {
            return (days / 7) + " tuần trước";
        }
    }

    private double calculateTrend(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double) (current - previous) / previous) * 100.0;
    }
}
