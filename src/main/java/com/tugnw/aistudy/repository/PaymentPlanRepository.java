package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {
    List<PaymentPlan> findByIsActiveTrue();
}
