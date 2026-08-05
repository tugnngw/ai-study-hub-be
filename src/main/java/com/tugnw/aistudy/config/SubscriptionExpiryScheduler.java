package com.tugnw.aistudy.config;

import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import com.tugnw.aistudy.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cleanup + backstop cho subscription lifecycle.
 * KHÔNG phải nơi duy nhất tạo FREE — chỉ dọn hàng tồn (expired) và gọi
 * ensureActiveSubscription cho account bị ảnh hưởng. User vẫn được heal
 * ngay lập tức qua login /me (lazy-heal) nếu scheduler chưa kịp chạy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 17 * * * *") // mỗi giờ, phút 17 (tránh đụng giờ chẵn)
    @Transactional
    public void expireAndRestoreFree() {
        List<Subscription> expired = subscriptionRepository
                .findExpiredActiveSubscriptions(SubscriptionStatus.ACTIVE, Instant.now());

        for (Subscription sub : expired) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            // Backstop: tạo FREE nếu account chưa có ACTIVE hợp lệ nào khác.
            // ensureActiveSubscription tự kiểm tra (idempotent, lock account).
            subscriptionService.ensureActiveSubscription(sub.getAccountId());
        }

        if (!expired.isEmpty()) {
            log.info("Subscription expiry: {} expired, FREE ensured", expired.size());
        }
    }
}
