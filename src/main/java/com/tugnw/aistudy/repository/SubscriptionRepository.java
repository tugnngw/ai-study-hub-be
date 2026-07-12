package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAccountIdAndStatus(UUID accountId, SubscriptionStatus status);

    List<Subscription> findByStatusAndEndDateBefore(SubscriptionStatus status, Instant date);

    List<Subscription> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Optional<Subscription> findByPlanIdAndStatus(UUID planId, SubscriptionStatus status);

    long countByPlan_IdAndStatus(UUID planId, SubscriptionStatus status);
}
