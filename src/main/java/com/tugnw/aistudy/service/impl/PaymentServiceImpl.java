package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.payment.*;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import com.tugnw.aistudy.domain.enums.Plan;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.PaymentTransactionRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.service.SubscriptionService;
import com.tugnw.aistudy.util.PayOSClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentPlanRepository planRepo;
    private final PaymentTransactionRepository txRepo;
    private final AccountRepository accountRepo;
    private final PayOSClient payOSClient;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;
    private final SubscriptionService subscriptionService;

    private static final AtomicLong orderCodeSeq = new AtomicLong(
        (System.currentTimeMillis() / 1000) % 1_000_000_000L + 100_000L
    );

    @Override
    public List<PaymentPlan> listActivePlans() {
        return planRepo.findByIsActiveTrue();
    }

    @Override
    @Transactional
    public PaymentStatusResponse getPaymentStatus(Long orderCode) {
        // Read path: không lock. Chỉ khi cần update EXPIRED → lock + re-check
        // (tránh đè status PAID mà webhook vừa set).
        PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + orderCode));

        if (tx.getStatus() == PaymentStatus.PENDING
                && tx.getExpiredAt() != null
                && tx.getExpiredAt().isBefore(Instant.now())) {
            // Lock-on-write: serialize với webhook/verify trên cùng transaction.
            tx = txRepo.findByPayosOrderCodeForUpdate(orderCode)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + orderCode));
            if (tx.getStatus() == PaymentStatus.PENDING
                    && tx.getExpiredAt() != null
                    && tx.getExpiredAt().isBefore(Instant.now())) {
                tx.setStatus(PaymentStatus.EXPIRED);
                tx = txRepo.save(tx);
            }
        }
        
        return PaymentStatusResponse.builder()
                .orderCode(tx.getPayosOrderCode())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : null)
                .transactionId(tx.getTransactionId())
                .build();
    }
    
    @Override
    @Transactional
    public PaymentResponse createPaymentLink(UUID userId, UUID planId) {
        PaymentPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("This plan is not available for purchase");
        }

        long finalAmount = subscriptionService.calculateUpgradePreview(userId, planId).getAmountToPay();

        log.info("Creating payment for user {}: plan={}, amount={}", userId, plan.getName(), finalAmount);

        long orderCode = orderCodeSeq.incrementAndGet();
        if (orderCode > 2_000_000_000L) {
            orderCodeSeq.set(100_000L);
            orderCode = orderCodeSeq.incrementAndGet();
        }

        var payOSResult = payOSClient.createCheckoutUrl(
                finalAmount, userId.toString(), orderCode, plan.getName());

        PaymentTransaction tx = PaymentTransaction.builder()
                .accountId(userId)
                .plan(plan)
                .payosOrderCode(orderCode)
                .amount(finalAmount)
                .status(PaymentStatus.PENDING)
                .description("Buy plan " + plan.getName())
                .expiredAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .build();

        txRepo.save(tx);

        return new PaymentResponse(
                payOSResult.getCheckoutUrl(),
                tx.getPayosOrderCode(),
                finalAmount,
                tx.getExpiredAt(),
                payOSResult.getQrCode()
        );
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signature) {
        try {
            if (!payOSClient.verifySignature(payload, signature)) {
                log.warn("Invalid webhook signature. Payload: {}", payload);
                throw new IllegalArgumentException("Invalid webhook signature");
            }

            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode == null || !jsonNode.has("data")) {
                throw new IllegalArgumentException("Invalid webhook data");
            }

            JsonNode dataNode = jsonNode.get("data");
            Long orderCode = dataNode.has("orderCode") ? dataNode.get("orderCode").asLong() : null;
            String statusCode = dataNode.has("code") ? dataNode.get("code").asText() : null;
            String transactionId = dataNode.has("transactionId") ? dataNode.get("transactionId").asText() : null;
            Long webhookAmount = dataNode.has("amount") ? dataNode.get("amount").asLong() : null;

            if (orderCode == null) {
                throw new IllegalArgumentException("Order code is required");
            }

            // Idempotency: lock row từ SELECT → 2 webhook cùng orderCode serialize.
            // Luồng sau thấy PAID → skip. Mọi write-path dùng chung method này.
            PaymentTransaction tx = txRepo.findByPayosOrderCodeForUpdate(orderCode)
                    .orElseThrow(() -> {
                        log.error("Transaction not found for orderCode: {}", orderCode);
                        return new IllegalArgumentException("Transaction not found: " + orderCode);
                    });

            if (webhookAmount != null && !webhookAmount.equals(tx.getAmount())) {
                log.error("Amount mismatch. Webhook: {}, Database: {}", webhookAmount, tx.getAmount());
                throw new IllegalArgumentException("Amount mismatch in webhook");
            }

            // PAID là terminal — sau lock, nếu đã PAID thì không xử lý lại
            // (không tạo subscription/quota/activity lần 2).
            if (tx.getStatus() == PaymentStatus.PAID) {
                log.info("Transaction {} already paid, skipping", orderCode);
                return;
            }

            PaymentStatus newStatus = mapPayOSStatus(statusCode);

            tx.setStatus(newStatus);
            if (transactionId != null) {
                tx.setTransactionId(transactionId);
            }

            PaymentTransaction savedTx = txRepo.save(tx);

            if (newStatus == PaymentStatus.PAID) {
                log.info("Payment successful for orderCode: {}, userId: {}", orderCode, tx.getAccountId());
                updateUserQuota(savedTx);
                subscriptionService.createSubscription(tx.getAccountId(), tx.getPlan(), savedTx);
            } else {
                log.info("Payment status updated to {} for orderCode: {}", newStatus, orderCode);
            }

        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook: " + e.getMessage(), e);
        }
    }

    private PaymentStatus mapPayOSStatus(String statusCode) {
        if (statusCode == null) return PaymentStatus.PENDING;

        String normalized = statusCode.toLowerCase().trim();
        switch (normalized) {
            case "00":
            case "success":
                return PaymentStatus.PAID;
            case "01":
            case "failed":
                return PaymentStatus.FAILED;
            case "02":
            case "cancelled":
                return PaymentStatus.CANCELLED;
            case "03":
            case "expired":
                return PaymentStatus.EXPIRED;
            default:
                return PaymentStatus.PENDING;
        }
    }

    @Transactional
    protected void updateUserQuota(PaymentTransaction tx) {
        try {
            Account account = accountRepo.findById(tx.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + tx.getAccountId()));

            PaymentPlan plan = tx.getPlan();
            if (plan == null)
                throw new IllegalArgumentException("Payment plan is null");

            Plan newPlan = mapPaymentPlanToAccountPlan(plan.getName());
            account.setPlan(newPlan); // account.plan chỉ để hiển thị — storage limit không còn ở Account

            Account savedAccount = accountRepo.save(account);

            activityLogService.logActivity(
                    savedAccount.getId(),
                    savedAccount.getUsername(),
                    ActivityType.USER_UPGRADE,
                    "Upgraded to " + newPlan + " plan",
                    "Plan: " + plan.getName() + ", Amount: " + tx.getAmount() + " VND"
            );

        } catch (OptimisticLockingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update user plan", e);
        }
    }

    private Plan mapPaymentPlanToAccountPlan(String planName) {
        if (planName == null)
            return Plan.FREE;

        String normalized = planName.trim().toUpperCase();

        if (normalized.contains("BASIC")) {
            return Plan.BASIC;
        } else if (normalized.contains("PRO")) {
            return Plan.PRO;
        } else if (normalized.contains("PREMIUM")) {
            return Plan.PREMIUM;
        }

        try {
            return Plan.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return Plan.FREE;
        }
    }

    @Override
    public List<PaymentTransactionResponse> getUserTransactions(UUID userId) {
        List<PaymentTransaction> transactions = txRepo.findByAccountIdOrderByCreatedAtDesc(userId);
        return transactions.stream()
                .map(this::convertToUserResponse)
                .toList();
    }

    @Override
    public Page<AdminTransactionResponse> getAllTransactions(Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findAllByOrderByCreatedAtDesc(pageable);
        return transactions.map(tx -> convertToAdminResponse(tx, loadAccountMap(transactions.getContent())));
    }

    @Override
    public Page<AdminTransactionResponse> getTransactionsByStatus(PaymentStatus status, Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        return transactions.map(tx -> convertToAdminResponse(tx, loadAccountMap(transactions.getContent())));
    }

    @Override
    public Optional<PaymentTransaction> getTransactionByOrderCode(Long orderCode) {
        return txRepo.findByPayosOrderCode(orderCode);
    }

    @Override
    @Transactional
    public void verifyAndProcessPayment(Long orderCode) {
        // Cùng cơ chế lock như webhook — manual verify + webhook không thể xử lý 2 lần.
        PaymentTransaction tx = txRepo.findByPayosOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for orderCode: " + orderCode));

        if (tx.getStatus() == PaymentStatus.PAID) return;

        tx.setStatus(PaymentStatus.PAID);
        tx.setTransactionId("MANUAL_" + System.currentTimeMillis());
        txRepo.save(tx);

        updateUserQuota(tx);
        subscriptionService.createSubscription(tx.getAccountId(), tx.getPlan(), tx);
    }

    @Override
    public Page<AdminTransactionResponse> getTransactionsByAccountId(UUID accountId, Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        return transactions.map(tx -> convertToAdminResponse(tx, loadAccountMap(transactions.getContent())));
    }

    private PaymentTransactionResponse convertToUserResponse(PaymentTransaction tx) {
        PaymentStatus currentStatus = tx.getStatus();
        if (currentStatus == PaymentStatus.PENDING && tx.getExpiredAt() != null && tx.getExpiredAt().isBefore(Instant.now())) {
            currentStatus = PaymentStatus.EXPIRED;
        }

        return PaymentTransactionResponse.builder()
                .id(tx.getId().toString())
                .accountId(tx.getAccountId().toString())
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : "N/A")
                .amount(tx.getAmount())
                .status(currentStatus.name())
                .description(tx.getDescription())
                .transactionId(tx.getTransactionId())
                .payosOrderCode(tx.getPayosOrderCode())
                .paymentMethod(tx.getPaymentMethod())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    /** Preload toàn bộ account của 1 page — 1 query findAllById thay N findById (chống N+1). */
    private Map<UUID, Account> loadAccountMap(List<PaymentTransaction> txs) {
        List<UUID> ids = txs.stream().map(PaymentTransaction::getAccountId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return accountRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));
    }

    private AdminTransactionResponse convertToAdminResponse(PaymentTransaction tx, Map<UUID, Account> accountMap) {
        Account account = accountMap.get(tx.getAccountId());

        PaymentStatus currentStatus = tx.getStatus();
        if (currentStatus == PaymentStatus.PENDING && tx.getExpiredAt() != null && tx.getExpiredAt().isBefore(Instant.now())) {
            currentStatus = PaymentStatus.EXPIRED;
        }

        return AdminTransactionResponse.builder()
                .id(tx.getId().toString())
                .accountId(tx.getAccountId())
                .userEmail(account != null ? account.getEmail() : "N/A")
                .userName(account != null ? account.getFullName() : "N/A")
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : "N/A")
                .amount(tx.getAmount())
                .status(currentStatus)
                .description(tx.getDescription())
                .transactionId(tx.getTransactionId())
                .payosOrderCode(tx.getPayosOrderCode())
                .paymentMethod(tx.getPaymentMethod())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
