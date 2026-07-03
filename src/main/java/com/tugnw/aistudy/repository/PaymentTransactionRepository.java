package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByPayosOrderCode(Long orderCode);
    Optional<PaymentTransaction> findByAccountIdAndStatus(UUID userId, PaymentStatus status);
}

