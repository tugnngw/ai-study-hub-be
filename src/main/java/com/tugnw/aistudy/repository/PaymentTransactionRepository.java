package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByPayosOrderCode(Long orderCode);

    /**
     * Idempotency: MỌI write-path của PaymentTransaction (webhook, manual verify,
     * poll-expire) phải dùng method này — lock row ngay từ SELECT → 2 luồng xử lý
     * cùng transaction serialize, luồng sau thấy status PAID → skip.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.payosOrderCode = :orderCode")
    Optional<PaymentTransaction> findByPayosOrderCodeForUpdate(@Param("orderCode") Long orderCode);
    Optional<PaymentTransaction> findByAccountIdAndStatus(UUID userId, PaymentStatus status);
    List<PaymentTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    Page<PaymentTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    // Revenue — backend tổng hợp, frontend không tự reduce transaction
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.status = :status")
    long sumAmountByStatus(@Param("status") PaymentStatus status);

    long countByStatus(PaymentStatus status);
}

