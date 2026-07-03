package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByPayosOrderCode(Long orderCode);
    Optional<PaymentTransaction> findByAccountIdAndStatus(UUID userId, PaymentStatus status);
    List<PaymentTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    Page<PaymentTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);
}

