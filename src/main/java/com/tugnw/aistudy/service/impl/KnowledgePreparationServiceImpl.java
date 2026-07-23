package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePreparationServiceImpl implements KnowledgePreparationService {

    private final DocumentRepository documentRepository;
    private final RagService ragService;
    private final QuotaService quotaService;

    private static final String SUMMARY_PROMPT = """
            Generate a concise, well-structured markdown summary of the following document content.
            Extract key concepts, definitions, and important details.
            Use headings, bullet points, and bold text for emphasis.
            Keep it informative but brief — capture what a student needs to know.

            Document:
            %s
            """;

    /** Per-document mutex — only one summary generation per document at a time. */
    private final ConcurrentHashMap<UUID, ReentrantLock> summaryLocks = new ConcurrentHashMap<>();

    @Override
    public String prepareKnowledge(Document doc, boolean force) throws Exception {
        StringBuilder merged = new StringBuilder();

        String summary = getOrGenerateSummary(doc, force);
        merged.append("--- ").append(doc.getTitle()).append(" ---\n")
                .append(summary).append("\n\n");

        return merged.toString().trim();
    }

    // ============ HELPER METHODS ============

    private String getOrGenerateSummary(Document doc, boolean force) throws Exception {
        // Fast path: already cached on this entity instance
        if (!force && doc.getSummary() != null && !doc.getSummary().trim().isEmpty())
            return doc.getSummary();

        // Double-check from DB (another thread may have committed since this instance loaded)
        if (!force) {
            Document fromDb = documentRepository.findById(doc.getId()).orElse(doc);
            if (fromDb.getSummary() != null && !fromDb.getSummary().trim().isEmpty()) {
                doc.setSummary(fromDb.getSummary());
                return fromDb.getSummary();
            }
        }

        // Application-level per-document mutex.
        // Only one thread enters the critical section per document ID.
        // No DB lock held — DB connections are free for other transactions.
        ReentrantLock lock = summaryLocks.computeIfAbsent(doc.getId(), k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock: Thread B (which was waiting) now sees
            // Thread A's committed summary if A already generated and saved it.
            if (!force) {
                Document current = documentRepository.findById(doc.getId()).orElse(doc);
                if (current.getSummary() != null && !current.getSummary().trim().isEmpty()) {
                    doc.setSummary(current.getSummary());
                    return current.getSummary();
                }
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
                UUID requesterId = userDetails.getAccount().getId();
                if (!quotaService.checkQuota(requesterId, "summary"))
                    throw new RuntimeException("Bạn đã đạt giới hạn số lượng tóm tắt AI cho gói hiện tại. Vui lòng nâng cấp gói để tiếp tục sử dụng.");
            }

            log.info("[SUMMARY] Generating summary for document {}", doc.getId());
            String rawText = ragService.extractTextFromDocument(doc.getId(), doc.getOwnerId());
            String prompt = String.format(SUMMARY_PROMPT, rawText);
            String markdownSummary = ragService.generateContent(prompt);

            doc.setSummary(markdownSummary);
            documentRepository.save(doc);

            log.info("[SUMMARY] Summary generation succeeded for document {}", doc.getId());
            return markdownSummary;
        } catch (Throwable e) {
            log.error("[SUMMARY] Summary generation failed for document {}: {}", doc.getId(), e.getMessage(), e);
            if (e instanceof Exception) {
                throw (Exception) e;
            }
            throw new RuntimeException("Summary generation failed", e);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                summaryLocks.remove(doc.getId(), lock);
            }
        }
    }
}
