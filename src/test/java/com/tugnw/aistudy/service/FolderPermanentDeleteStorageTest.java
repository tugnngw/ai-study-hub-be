package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quota.StorageQuota;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.ChatSession;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.PaymentPlan;
import com.tugnw.aistudy.domain.entity.Subscription;
import com.tugnw.aistudy.domain.enums.SubscriptionStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.PaymentPlanRepository;
import com.tugnw.aistudy.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-1: Folder permanent delete phải trừ Storage Counter đúng tổng fileSize
 * của TOÀN BỘ documents trong folder (mọi status, kể cả soft-deleted/BANNED/REJECT),
 * aggregate MỘT lần, cùng transaction.
 */
@SpringBootTest
class FolderPermanentDeleteStorageTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentPlanRepository paymentPlanRepository;
    @Autowired private FolderRepository folderRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ChatSessionRepository chatSessionRepository;
    @Autowired private FolderService folderService;
    @Autowired private StorageQuotaService storageQuotaService;

    private UUID accountId;
    private UUID folderId;

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(Account.builder()
                .username("folder_test_" + UUID.randomUUID().toString().substring(0, 8))
                .email("folder_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.local")
                .passwordHash("x")
                .usedStorageBytes(0L)
                .build());
        accountId = account.getId();

        PaymentPlan free = paymentPlanRepository.findByIsActiveTrue().stream()
                .filter(p -> "FREE".equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElseGet(() -> paymentPlanRepository.save(PaymentPlan.builder()
                        .name("FREE")
                        .price(0L)
                        .storageGb(1.0)
                        .isActive(true)
                        .durationDays(-1)
                        .build()));

        subscriptionRepository.save(Subscription.builder()
                .accountId(accountId)
                .plan(free)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.Instant.now())
                .endDate(null)
                .pricePaid(0L)
                .storageGbGranted(1.0)
                .aiQuestionsGranted(0)
                .flashcardLimitGranted(0)
                .questionLimitGranted(0)
                .summaryLimitGranted(0)
                .chatLimitGranted(0)
                .tierGranted(0)
                .maxStorageGb(1.0)
                .build());

        Folder folder = folderRepository.save(Folder.builder()
                .ownerId(accountId)
                .name("test_folder_" + UUID.randomUUID().toString().substring(0, 8))
                .build());
        folderId = folder.getId();

        // Setup: folder rỗng — counter bắt đầu 0 (không có doc để reserve)
        assertEquals(0L, used());
    }

    private void addDoc(String status, long size, LocalDateTime deletedAt) {
        documentRepository.save(Document.builder()
                .ownerId(accountId)
                .folderId(folderId)
                .title("doc_" + UUID.randomUUID().toString().substring(0, 8))
                .status(status)
                .fileSize(size)
                .deletedAt(deletedAt)
                .build());
        // Mô phỏng upload: counter tăng qua reserveStorage (như flow thật)
        storageQuotaService.reserveStorage(accountId, size);
    }

    @AfterEach
    void cleanup() {
        documentRepository.deleteAll(
                documentRepository.findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId));
        documentRepository.deleteAll(
                documentRepository.findByDeletedAtIsNotNull().stream()
                        .filter(d -> folderId.equals(d.getFolderId()))
                        .toList());
        folderRepository.deleteAll();
        subscriptionRepository.deleteAll(
                subscriptionRepository.findByAccountIdOrderByCreatedAtDesc(accountId));
        accountRepository.deleteById(accountId);
    }

    private long used() {
        return storageQuotaService.getQuota(accountId).storageUsedBytes();
    }

    @Test
    @Transactional
    void permanentDeleteFolder_subtractsTotalOfAllDocs() {
        // 3 doc bình thường (READY)
        addDoc("READY", 1000L, null);
        addDoc("READY", 2000L, null);
        addDoc("READY", 3000L, null);
        // 1 doc soft-deleted, 1 doc BANNED
        addDoc("READY", 5000L, LocalDateTime.now().minusDays(1));
        addDoc("BANNED", 7000L, null);

        assertEquals(6000L + 5000L + 7000L, used());

        // Soft-delete folder (bắt buộc trước permanent)
        folderRepository.findById(folderId).ifPresent(f -> {
            f.setDeletedAt(LocalDateTime.now());
            folderRepository.save(f);
        });

        folderService.permanentDeleteFolder(folderId, accountId);

        // used giảm đúng TỔNG — kể cả soft-deleted + banned
        assertEquals(0L, used());
    }

    @Test
    @Transactional
    void permanentDeleteFolder_emptyFolder_doesNotChangeUsed() {
        // Không có document — used giữ nguyên 0
        assertEquals(0L, used());
        folderRepository.findById(folderId).ifPresent(f -> {
            f.setDeletedAt(LocalDateTime.now());
            folderRepository.save(f);
        });
        folderService.permanentDeleteFolder(folderId, accountId);
        assertEquals(0L, used());
    }

    @Test
    @Transactional
    void permanentDeleteFolder_mixedStatusSubtractsAll() {
        addDoc("REJECT", 1000L, null);
        addDoc("REPORTED", 2000L, null);
        addDoc("COMPLETED", 4000L, LocalDateTime.now().minusDays(2)); // soft-deleted COMPLETED

        folderRepository.findById(folderId).ifPresent(f -> {
            f.setDeletedAt(LocalDateTime.now());
            folderRepository.save(f);
        });
        folderService.permanentDeleteFolder(folderId, accountId);

        assertEquals(0L, used());
    }

    @Test
    @Transactional
    void permanentDeleteFolder_deletesDocumentRowsAndFolder() {
        addDoc("READY", 1000L, null);
        addDoc("READY", 2000L, null);

        folderRepository.findById(folderId).ifPresent(f -> {
            f.setDeletedAt(LocalDateTime.now());
            folderRepository.save(f);
        });
        folderService.permanentDeleteFolder(folderId, accountId);

        // Document rows bị xóa THẬT (không còn trong DB, kể cả soft-deleted)
        assertEquals(0, documentRepository.findByFolderId(folderId).size());
        // Folder row bị xóa
        assertTrue(folderRepository.findById(folderId).isEmpty());
    }

    @Test
    @Transactional
    void permanentDeleteFolder_deletesChatSessions() {
        addDoc("READY", 1000L, null);
        Document doc = documentRepository.findByFolderId(folderId).get(0);

        // Tạo chat session cho doc
        chatSessionRepository.save(ChatSession.builder()
                .accountId(accountId)
                .documentId(doc.getId())
                .title("chat_" + UUID.randomUUID().toString().substring(0, 6))
                .build());

        folderRepository.findById(folderId).ifPresent(f -> {
            f.setDeletedAt(LocalDateTime.now());
            folderRepository.save(f);
        });
        folderService.permanentDeleteFolder(folderId, accountId);

        // Chat session bị xóa thật
        assertEquals(0, chatSessionRepository.findByDocumentId(doc.getId()).size());
    }
}
