package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {
    List<PaymentPlan> findByIsActiveTrue();
    boolean existsByName(String name);
}
