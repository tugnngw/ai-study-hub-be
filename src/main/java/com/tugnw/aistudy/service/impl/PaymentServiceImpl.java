package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.payment.AdminTransactionResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.dto.payment.PaymentTransactionResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
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

import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final com.tugnw.aistudy.repository.SubscriptionRepository subscriptionRepository;
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
    public PaymentResponse createPaymentLink(UUID userId, UUID planId) {
        PaymentPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        // ===== BẮT ĐẦU: TÍNH TOÁN BÙ TRỪ KHI NÂNG CẤP =====
        long finalAmount = plan.getPrice();
        
        // Tìm subscription active hiện tại của user
        List<com.tugnw.aistudy.domain.entity.Subscription> activeSubs = 
                subscriptionRepository.findByAccountIdAndStatus(userId, com.tugnw.aistudy.domain.enums.SubscriptionStatus.ACTIVE);
                
        if (!activeSubs.isEmpty()) {
            com.tugnw.aistudy.domain.entity.Subscription activeSub = activeSubs.get(0);
            java.time.Instant now = java.time.Instant.now();
            
            // Nếu gói đang dùng chưa hết hạn
            if (activeSub.getEndDate() != null && activeSub.getEndDate().isAfter(now)) {
                // Tính số ngày còn lại
                long remainingMillis = activeSub.getEndDate().toEpochMilli() - now.toEpochMilli();
                int remainingDays = (int) Math.ceil((double) remainingMillis / 86_400_000);
                
                if (remainingDays > 0) {
                    PaymentPlan currentPlan = activeSub.getPlan();
                    int currentDuration = currentPlan.getDurationDays() != null && currentPlan.getDurationDays() > 0 
                            ? currentPlan.getDurationDays() : 30;
                            
                    // Giá trị còn lại của gói cũ = (Giá gói cũ / Thời hạn gói cũ) * Số ngày còn lại
                    long remainingValue = Math.round(((double) currentPlan.getPrice() / currentDuration) * remainingDays);
                    
                    // Tính số tiền phải trả = Giá gói mới - Giá trị còn lại
                    finalAmount = Math.max(0, plan.getPrice() - remainingValue);
                    
                    log.info("Proration calculated for user {}: currentPlan={}, remainingDays={}, remainingValue={}, targetPlan={}, finalAmount={}",
                            userId, currentPlan.getName(), remainingDays, remainingValue, plan.getName(), finalAmount);
                }
            }
        }
        // ===== KẾT THÚC: TÍNH TOÁN BÙ TRỪ =====

        // ⚠️ orderCode MUST be <= 2,147,483,647 (PayOS requirement)
        // Use AtomicLong to ensure unique, monotonically increasing order codes
        long orderCode = orderCodeSeq.incrementAndGet();
        if (orderCode > 2_000_000_000L) {
            orderCodeSeq.set(100_000L);
            orderCode = orderCodeSeq.incrementAndGet();
        }

        log.info("Generated orderCode: {}", orderCode);

        // Tạo link thanh toán với giá tiền ĐÃ ĐƯỢC BÙ TRỪ
        String checkoutUrl = payOSClient.createCheckoutUrl(
                finalAmount, userId.toString(), orderCode, plan.getName());

        PaymentTransaction tx = PaymentTransaction.builder()
                .accountId(userId)
                .plan(plan)
                .payosOrderCode(orderCode)
                .amount(finalAmount) // Lưu đúng số tiền thực tế user phải trả
                .status(PaymentStatus.PENDING)
                .description("Buy plan " + plan.getName() + (finalAmount < plan.getPrice() ? " (Upgraded)" : ""))
                .expiredAt(java.time.Instant.now().plus(3, ChronoUnit.MINUTES))
                .build();

        txRepo.save(tx);
        log.info("Created payment link for user: {}, orderCode: {}, amount: {}", userId, orderCode, finalAmount);

        return new PaymentResponse(checkoutUrl, tx.getPayosOrderCode(), finalAmount);
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signature) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║          WEBHOOK RECEIVED FROM PAYOS                         ║");
        log.info("╚══════════════════════════════════════════════════════════════");
        log.info("📅 TIMESTAMP: {}", java.time.Instant.now());

        try {
            // 1. CRYPTOGRAPHIC SIGNATURE VERIFICATION (before any business logic)
            if (!payOSClient.verifySignature(payload, signature)) {
                log.error("❌ WEBHOOK SIGNATURE VERIFICATION FAILED — rejecting");
                throw new IllegalArgumentException("Invalid webhook signature");
            }
            log.info("✅ WEBHOOK SIGNATURE VERIFIED");

            // 2. Parse payload
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode == null || !jsonNode.has("data")) {
                log.error("❌ INVALID WEBHOOK: no 'data' field in payload");
                throw new IllegalArgumentException("Invalid webhook data");
            }

            JsonNode dataNode = jsonNode.get("data");
            Long orderCode = dataNode.has("orderCode") ? dataNode.get("orderCode").asLong() : null;
            String statusCode = dataNode.has("code") ? dataNode.get("code").asText() : null;
            String transactionId = dataNode.has("transactionId") ? dataNode.get("transactionId").asText() : null;
            Long webhookAmount = dataNode.has("amount") ? dataNode.get("amount").asLong() : null;

            log.info("╠ orderCode={} statusCode={}", orderCode, statusCode);

            if (orderCode == null) {
                log.error("Order code is null in webhook payload");
                throw new IllegalArgumentException("Order code is required");
            }

            // 3. Find existing transaction — reject unknown order codes
            PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode)
                    .orElseThrow(() -> {
                        log.error("❌ No transaction found for orderCode={} — rejecting", orderCode);
                        return new IllegalArgumentException("Transaction not found: " + orderCode);
                    });

            log.info("Found transaction: id={}, status={}, amount={}",
                    tx.getId(), tx.getStatus(), tx.getAmount());

            // 4. Cross-validate amount against stored transaction
            if (webhookAmount != null && !webhookAmount.equals(tx.getAmount())) {
                log.error("❌ Amount mismatch for orderCode={}: webhook says {}, stored transaction says {}",
                        orderCode, webhookAmount, tx.getAmount());
                throw new IllegalArgumentException("Amount mismatch in webhook");
            }

            // 5. Idempotency check — skip if already processed
            if (tx.getStatus() == PaymentStatus.PAID) {
                log.warn("Transaction {} already paid, skipping", orderCode);
                return;
            }

            // 6. Map status
            PaymentStatus newStatus = mapPayOSStatus(statusCode);
            log.info("Status mapping: '{}' → '{}'", statusCode, newStatus);

            // 6. Update transaction
            tx.setStatus(newStatus);
            if (transactionId != null) {
                tx.setTransactionId(transactionId);
            }
            PaymentTransaction savedTx = txRepo.save(tx);
            log.info("💾 TRANSACTION SAVED TO DATABASE:");
            log.info("   - ID: {}", savedTx.getId());
            log.info("   - OrderCode: {}", savedTx.getPayosOrderCode());
            log.info("   - Status: {}", savedTx.getStatus());
            log.info("   - AccountId: {}", savedTx.getAccountId());

            // Handle by status
            switch (newStatus) {
                case PAID:
                    log.info("╔══════════════════════════════════════════════════════════════");
                    log.info("║       ✅ PAYMENT SUCCESSFUL - UPGRADING USER PLAN             ║");
                    log.info("╚══════════════════════════════════════════════════════════════");
                    updateUserQuota(tx);
                    subscriptionService.createSubscription(tx.getAccountId(), tx.getPlan(), tx);
                    log.info("╔══════════════════════════════════════════════════════════════");
                    log.info("║       ✅ USER PLAN UPGRADE COMPLETED                          ║");
                    log.info("╚══════════════════════════════════════════════════════════════");
                    break;
                case CANCELLED:
                    log.info("⚠️ Transaction CANCELLED: {}", orderCode);
                    break;
                case FAILED:
                    log.info("❌ Transaction FAILED: {}", orderCode);
                    break;
                case EXPIRED:
                    log.info("⏰ Transaction EXPIRED: {}", orderCode);
                    break;
                default:
                    log.warn("⚠️ Unhandled status {} for transaction {}", newStatus, orderCode);
            }

            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║          WEBHOOK PROCESSING COMPLETED SUCCESSFULLY           ║");
            log.info("╚══════════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("╔══════════════════════════════════════════════════════════════");
            log.error("║       ❌ WEBHOOK PROCESSING FAILED                            ║");
            log.error("╚══════════════════════════════════════════════════════════════");
            log.error("Error type: {}", e.getClass().getSimpleName());
            log.error("Error message: {}", e.getMessage());
            log.error("Stack trace:", e);
            throw new RuntimeException("Failed to process webhook: " + e.getMessage(), e);
        }
    }

    private PaymentStatus mapPayOSStatus(String statusCode) {
        log.info("🔄 mapPayOSStatus() called with: '{}'", statusCode);
        
        if (statusCode == null) {
            log.warn("⚠️ Status code is NULL, returning PENDING");
            return PaymentStatus.PENDING;
        }

        String normalized = statusCode.toLowerCase().trim();
        PaymentStatus result;
        
        switch (normalized) {
            case "00":
            case "success":
                result = PaymentStatus.PAID;
                log.info("✅ Status '{}' mapped to PAID", statusCode);
                break;
            case "01":
            case "failed":
                result = PaymentStatus.FAILED;
                log.info("❌ Status '{}' mapped to FAILED", statusCode);
                break;
            case "02":
            case "cancelled":
                result = PaymentStatus.CANCELLED;
                log.info("⚠️ Status '{}' mapped to CANCELLED", statusCode);
                break;
            case "03":
            case "expired":
                result = PaymentStatus.EXPIRED;
                log.info("⏰ Status '{}' mapped to EXPIRED", statusCode);
                break;
            default:
                log.warn("⚠️ Unknown status code: '{}', defaulting to PENDING", statusCode);
                result = PaymentStatus.PENDING;
        }
        
        return result;
    }

    @Transactional
    protected void updateUserQuota(PaymentTransaction tx) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║              updateUserQuota() CALLED                         ║");
        log.info("╚══════════════════════════════════════════════════════════════");
        log.info("   Transaction ID: {}", tx.getId());
        log.info("   Account ID: {}", tx.getAccountId());
        log.info("   PaymentPlan: {}", tx.getPlan() != null ? tx.getPlan().getName() : "NULL");
        
        try {
            Account account = accountRepo.findById(tx.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + tx.getAccountId()));
            
            log.info("📋 ACCOUNT BEFORE UPDATE:");
            log.info("   - ID: {}", account.getId());
            log.info("   - Email: {}", account.getEmail());
            log.info("   - Current Plan: {}", account.getPlan());

            PaymentPlan plan = tx.getPlan();
            if (plan == null) {
                log.error("❌ PLAN IS NULL - Cannot upgrade user");
                throw new IllegalArgumentException("Payment plan is null");
            }
            
            log.info("📋 PAYMENT PLAN INFO:");
            log.info("   - Plan ID: {}", plan.getId());
            log.info("   - Plan Name: {}", plan.getName());
            log.info("   - Plan Price: {} VND", plan.getPrice());

            com.tugnw.aistudy.domain.enums.Plan newPlan = mapPaymentPlanToAccountPlan(plan.getName());
            log.info("🔄 MAPPING: '{}' → '{}'", plan.getName(), newPlan);

            account.setPlan(newPlan);
            if (plan.getStorageGb() != null) {
                account.setStorageGb(plan.getStorageGb());
            }
            Account savedAccount = accountRepo.save(account);

            // Log upgrade activity
            activityLogService.logActivity(
                    savedAccount.getId(),
                    savedAccount.getUsername(),
                    ActivityType.USER_UPGRADE,
                    "Upgraded to " + newPlan + " plan",
                    "Plan: " + plan.getName() + ", Amount: " + tx.getAmount() + " VND"
            );
            
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║              ✅ ACCOUNT UPDATED SUCCESSFULLY                  ║");
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║ Account ID    : {}                                     ", savedAccount.getId());
            log.info("║ Email         : {}                                     ", savedAccount.getEmail());
            log.info("║ NEW PLAN      : {}                                     ", savedAccount.getPlan());
            log.info("║ STORAGE       : {} GB                                  ", savedAccount.getStorageGb());
            log.info("║ Updated At    : {}                                     ", savedAccount.getUpdatedAt());
            log.info("╚══════════════════════════════════════════════════════════════");

        } catch (OptimisticLockingFailureException e) {
            log.error("❌ Race condition during plan upgrade for user {}", tx.getAccountId());
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to update user plan for transaction {}", tx.getId());
            log.error("   Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update user plan", e);
        }
    }

    private com.tugnw.aistudy.domain.enums.Plan mapPaymentPlanToAccountPlan(String planName) {
        if (planName == null) {
            return com.tugnw.aistudy.domain.enums.Plan.FREE;
        }
        
        String normalized = planName.trim().toUpperCase();
        
        if (normalized.contains("BASIC")) {
            return com.tugnw.aistudy.domain.enums.Plan.BASIC;
        } else if (normalized.contains("PRO")) {
            return com.tugnw.aistudy.domain.enums.Plan.PRO;
        } else if (normalized.contains("PREMIUM")) {
            return com.tugnw.aistudy.domain.enums.Plan.PREMIUM;
        }
        
        try {
            return com.tugnw.aistudy.domain.enums.Plan.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Could not map plan name '{}' to enum, defaulting to FREE", planName);
            return com.tugnw.aistudy.domain.enums.Plan.FREE;
        }
    }

    @Override
    public List<PaymentTransactionResponse> getUserTransactions(UUID userId) {
        List<PaymentTransaction> transactions = txRepo.findByAccountIdOrderByCreatedAtDesc(userId);
        return transactions.stream()
                .map(this::convertToUserResponse)
                .toList();
    }

    private PaymentTransactionResponse convertToUserResponse(PaymentTransaction tx) {
        return PaymentTransactionResponse.builder()
                .id(tx.getId().toString())
                .accountId(tx.getAccountId().toString())
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : "N/A")
                .amount(tx.getAmount())
                .status(tx.getStatus().name())
                .description(tx.getDescription())
                .transactionId(tx.getTransactionId())
                .payosOrderCode(tx.getPayosOrderCode())
                .paymentMethod(tx.getPaymentMethod())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    @Override
    public Page<AdminTransactionResponse> getAllTransactions(Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findAllByOrderByCreatedAtDesc(pageable);
        return transactions.map(this::convertToAdminResponse);
    }

    @Override
    public Page<AdminTransactionResponse> getTransactionsByStatus(PaymentStatus status, Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        return transactions.map(this::convertToAdminResponse);
    }

    private AdminTransactionResponse convertToAdminResponse(PaymentTransaction tx) {
        Account account = accountRepo.findById(tx.getAccountId())
                .orElse(null);
        
        return AdminTransactionResponse.builder()
                .id(tx.getId().toString())
                .accountId(tx.getAccountId())
                .userEmail(account != null ? account.getEmail() : "N/A")
                .userName(account != null ? account.getFullName() : "N/A")
                .planName(tx.getPlan() != null ? tx.getPlan().getName() : "N/A")
                .amount(tx.getAmount())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .transactionId(tx.getTransactionId())
                .payosOrderCode(tx.getPayosOrderCode())
                .paymentMethod(tx.getPaymentMethod())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    @Override
    public Optional<PaymentTransaction> getTransactionByOrderCode(Long orderCode) {
        return txRepo.findByPayosOrderCode(orderCode);
    }

    @Override
    @Transactional
    public void verifyAndProcessPayment(Long orderCode) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║       MANUAL PAYMENT VERIFICATION TRIGGERED                   ║");
        log.info("╚══════════════════════════════════════════════════════════════");
        log.info("OrderCode: {}", orderCode);

        PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for orderCode: " + orderCode));

        log.info("Found transaction: id={}, status={}, amount={}, accountId={}",
                tx.getId(), tx.getStatus(), tx.getAmount(), tx.getAccountId());

        if (tx.getStatus() == PaymentStatus.PAID) {
            log.warn("Transaction {} already paid, skipping", orderCode);
            return;
        }

        tx.setStatus(PaymentStatus.PAID);
        tx.setTransactionId("MANUAL_" + System.currentTimeMillis());
        txRepo.save(tx);
        
        log.info("✅ Transaction {} status updated to PAID", orderCode);

        updateUserQuota(tx);
        subscriptionService.createSubscription(tx.getAccountId(), tx.getPlan(), tx);

        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║       MANUAL VERIFICATION COMPLETED                           ║");
        log.info("╚══════════════════════════════════════════════════════════════");
    }

    @Override
    public Page<AdminTransactionResponse> getTransactionsByAccountId(UUID accountId, Pageable pageable) {
        Page<PaymentTransaction> transactions = txRepo.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        return transactions.map(this::convertToAdminResponse);
    }
}