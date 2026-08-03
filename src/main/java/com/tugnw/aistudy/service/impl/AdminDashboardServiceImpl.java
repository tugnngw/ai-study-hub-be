package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.admin.ActivityResponse;
import com.tugnw.aistudy.domain.dto.admin.DashboardStatsResponse;
import com.tugnw.aistudy.domain.entity.ActivityLog;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentTransactionRepository;
import com.tugnw.aistudy.repository.ActivityLogRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public DashboardStatsResponse getDashboardStats() {
        Instant now = Instant.now();
        Instant lastWeek = now.minus(7, ChronoUnit.DAYS);
        Instant twoWeeksAgo = now.minus(14, ChronoUnit.DAYS);

        LocalDateTime lastWeekLdt = LocalDateTime.ofInstant(lastWeek, ZoneId.systemDefault());
        LocalDateTime twoWeeksAgoLdt = LocalDateTime.ofInstant(twoWeeksAgo, ZoneId.systemDefault());

        long totalUsers = accountRepository.count();
        long totalDocs = documentRepository.count();

        long usersLastWeek = accountRepository.countByCreatedAtAfter(lastWeek);
        long usersPrevWeek = accountRepository.countByCreatedAtAfter(twoWeeksAgo) - usersLastWeek;

        long docsLastWeek = documentRepository.countByCreatedAtAfter(lastWeekLdt);
        long docsPrevWeek = documentRepository.countByCreatedAtAfter(twoWeeksAgoLdt) - docsLastWeek;

        Long downloadsLastWeek = activityLogRepository.countByActionTypeAndCreatedAtAfter(
                ActivityType.DOCUMENT_DOWNLOAD, lastWeek);
        Long downloadsPrevWeek = activityLogRepository.countByActionTypeAndCreatedAtAfter(
                ActivityType.DOCUMENT_DOWNLOAD, twoWeeksAgo);
        downloadsPrevWeek = downloadsPrevWeek - (downloadsLastWeek != null ? downloadsLastWeek : 0);

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .usersLastWeek(usersLastWeek)
                .usersPrevWeek(usersPrevWeek)
                .totalDocs(totalDocs)
                .docsLastWeek(docsLastWeek)
                .docsPrevWeek(docsPrevWeek)
                .totalDownloads(downloadsLastWeek != null ? downloadsLastWeek : 0L)
                .downloadsLastWeek(downloadsLastWeek != null ? downloadsLastWeek : 0L)
                .downloadsPrevWeek(downloadsPrevWeek != null ? downloadsPrevWeek : 0L)
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

    @Override
    public com.tugnw.aistudy.domain.dto.payment.RevenueStatsResponse getRevenueStats() {
        long totalPaid = paymentTransactionRepository.countByStatus(PaymentStatus.PAID);
        long totalFailed = paymentTransactionRepository.countByStatus(PaymentStatus.FAILED);
        Long totalRevenue = paymentTransactionRepository.sumAmountByStatus(PaymentStatus.PAID);
        
        return com.tugnw.aistudy.domain.dto.payment.RevenueStatsResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : 0)
                .totalPaidTransactions(totalPaid)
                .totalFailedTransactions(totalFailed)
                .successRate(totalPaid + totalFailed > 0 ? (double) totalPaid * 100 / (totalPaid + totalFailed) : 0)
                .build();
    }

    private ActivityResponse mapToActivityResponse(ActivityLog log) {
        return ActivityResponse.builder()
                .id(log.getId().toString())
                .title(log.getDescription())
                .actor(log.getUserName() != null ? log.getUserName() : "System")
                .type(log.getActionType().name())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
