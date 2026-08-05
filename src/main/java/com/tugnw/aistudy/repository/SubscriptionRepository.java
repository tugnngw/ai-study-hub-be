package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByAccountIdAndStatus(UUID accountId, SubscriptionStatus status);

    /** ACTIVE nhưng end_date < now — cho scheduler cleanup. */
    @Query("SELECT s FROM Subscription s WHERE s.status = :status AND s.endDate IS NOT NULL AND s.endDate < :now")
    List<Subscription> findExpiredActiveSubscriptions(@Param("status") SubscriptionStatus status, @Param("now") Instant now);

    List<Subscription> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    long countByPlan_IdAndStatus(UUID planId, SubscriptionStatus status);

    Optional<Subscription> findFirstByAccountIdAndStatusOrderByEndDateDesc(UUID accountId, SubscriptionStatus status);
}
