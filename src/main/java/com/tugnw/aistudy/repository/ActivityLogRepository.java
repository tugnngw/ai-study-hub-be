package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.ActivityLog;
import com.tugnw.aistudy.domain.enums.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    Long countByActionTypeAndCreatedAtAfter(ActivityType actionType, Instant after);
}
