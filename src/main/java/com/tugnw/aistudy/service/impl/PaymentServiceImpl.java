package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.payment.PaymentResponse;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.PaymentTransaction;
import com.tugnw.aistudy.domain.enums.PaymentStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.PaymentTransactionRepository;
import com.tugnw.aistudy.service.PaymentService;
import com.tugnw.aistudy.util.PayOSClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentPlanRepository planRepo;
    private final PaymentTransactionRepository txRepo;
    private final AccountRepository accountRepo;
    private final PayOSClient payOSClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PaymentPlan> listActivePlans() {
        return planRepo.findByIsActiveTrue();
    }

    @Override
    @Transactional
    public PaymentResponse createPaymentLink(UUID userId, Long planId) {
        PaymentPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        // ⚠️ orderCode MUST be <= 2,147,483,647 (PayOS requirement)
        // Use nanoTime to ensure unique, then mod with 2B to keep within limit
        int safeOrderCode = (int) (System.nanoTime() % 2_000_000_000L);
        if (safeOrderCode < 100000) {
            safeOrderCode += 100000;
        }
        Long orderCode = (long) safeOrderCode;

        log.info("Generated orderCode: {}", orderCode);

        String checkoutUrl = payOSClient.createCheckoutUrl(
                plan.getPrice(), userId.toString(), orderCode, plan.getName());

        PaymentTransaction tx = PaymentTransaction.builder()
                .accountId(userId)
                .plan(plan)
                .payosOrderCode(orderCode)
                .amount(plan.getPrice())
                .status(PaymentStatus.PENDING)
                .description("Buy plan " + plan.getName())
                .build();

        txRepo.save(tx);
        log.info("Created payment link for user: {}, orderCode: {}", userId, orderCode);

        return new PaymentResponse(checkoutUrl, tx.getPayosOrderCode(), plan.getPrice());
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signature) {
        log.info("=== WEBHOOK PROCESSING START ===");
        log.info("Payload: {}", payload);
        log.info("Signature from header: {}", signature);

        // ⚠️ SIGNATURE CHECK DISABLED - DEVELOPMENT MODE ONLY
        log.info("⚠️ SIGNATURE CHECK DISABLED - DEVELOPMENT MODE ONLY");

        try {
            // Parse payload
            JsonNode jsonNode = objectMapper.readTree(payload);

            if (jsonNode == null || !jsonNode.has("data")) {
                log.error("Invalid webhook payload: no data field");
                throw new IllegalArgumentException("Invalid webhook data");
            }

            JsonNode dataNode = jsonNode.get("data");
            Long orderCode = dataNode.has("orderCode") ? dataNode.get("orderCode").asLong() : null;
            String statusCode = jsonNode.has("code") ? jsonNode.get("code").asText() : null;
            String transactionId = dataNode.has("transactionId") ? dataNode.get("transactionId").asText() : null;
            Long amount = dataNode.has("amount") ? dataNode.get("amount").asLong() : null;

            log.info("OrderCode: {}, StatusCode: {}, TransactionId: {}, Amount: {}",
                    orderCode, statusCode, transactionId, amount);

            if (orderCode == null) {
                log.error("Order code is null in webhook payload");
                throw new IllegalArgumentException("Order code is required");
            }

            // Find transaction - if not found, auto create for development
            PaymentTransaction tx = txRepo.findByPayosOrderCode(orderCode)
                    .orElseGet(() -> {
                        log.warn("Transaction not found for orderCode: {}, creating new one for test", orderCode);

                        Account account = accountRepo.findAll().stream().findFirst()
                                .orElseThrow(() -> new RuntimeException("No account found in database"));

                        PaymentPlan plan = planRepo.findAll().stream().findFirst()
                                .orElseThrow(() -> new RuntimeException("No plan found in database"));

                        PaymentTransaction newTx = PaymentTransaction.builder()
                                .accountId(account.getId())
                                .plan(plan)
                                .payosOrderCode(orderCode)
                                .amount(amount != null ? amount : plan.getPrice())
                                .status(PaymentStatus.PENDING)
                                .description("Auto created from webhook (orderCode: " + orderCode + ")")
                                .build();

                        PaymentTransaction saved = txRepo.save(newTx);
                        log.info("✅ Auto created transaction with id: {}, orderCode: {}", saved.getId(), saved.getPayosOrderCode());
                        return saved;
                    });

            log.info("Found/Auto-created transaction: id={}, status={}, amount={}",
                    tx.getId(), tx.getStatus(), tx.getAmount());

            // Idempotency
            if (tx.getStatus() == PaymentStatus.PAID) {
                log.warn("Transaction {} already paid, skipping", orderCode);
                return;
            }

            // Map status
            PaymentStatus newStatus = mapPayOSStatus(statusCode);
            log.info("Mapping status '{}' to '{}'", statusCode, newStatus);

            // Update transaction
            tx.setStatus(newStatus);
            if (transactionId != null) {
                tx.setTransactionId(transactionId);
            }
            txRepo.save(tx);
            log.info("Updated transaction {} status to {}", orderCode, newStatus);

            // Handle by status
            switch (newStatus) {
                case PAID:
                    log.info("✅ Processing paid transaction: {}", orderCode);
                    updateUserQuota(tx);
                    break;
                case CANCELLED:
                    log.info("Transaction cancelled: {}", orderCode);
                    break;
                case FAILED:
                    log.info("Transaction failed: {}", orderCode);
                    break;
                case EXPIRED:
                    log.info("Transaction expired: {}", orderCode);
                    break;
                default:
                    log.warn("Unhandled status {} for transaction {}", newStatus, orderCode);
            }

            log.info("=== WEBHOOK PROCESSING COMPLETED ===");

        } catch (Exception e) {
            log.error("Unexpected error processing webhook", e);
            throw new RuntimeException("Failed to process webhook: " + e.getMessage(), e);
        }
    }

    private PaymentStatus mapPayOSStatus(String statusCode) {
        if (statusCode == null) {
            return PaymentStatus.PENDING;
        }

        switch (statusCode.toLowerCase()) {
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
                log.warn("Unknown status code: {}, defaulting to PENDING", statusCode);
                return PaymentStatus.PENDING;
        }
    }

    @Transactional
    protected void updateUserQuota(PaymentTransaction tx) {
        try {
            Account account = accountRepo.findById(tx.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + tx.getAccountId()));

            PaymentPlan plan = tx.getPlan();
            log.info("Upgrading user {} to plan {}", account.getId(), plan.getName());

            // Convert plan name string to Plan enum
            com.tugnw.aistudy.domain.enums.Plan newPlan = com.tugnw.aistudy.domain.enums.Plan.valueOf(plan.getName().toUpperCase());
            account.setPlan(newPlan);

            accountRepo.save(account);
            log.info("✅ User {} plan upgraded successfully to {}", account.getId(), newPlan);

        } catch (OptimisticLockingFailureException e) {
            log.error("Race condition during plan upgrade for user {}", tx.getAccountId());
            throw e;
        } catch (Exception e) {
            log.error("Failed to update user plan for transaction {}", tx.getId(), e);
            throw new RuntimeException("Failed to update user plan", e);
        }
    }

    @Override
    public Optional<PaymentTransaction> getTransactionByOrderCode(Long orderCode) {
        return txRepo.findByPayosOrderCode(orderCode);
    }
}