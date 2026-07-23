package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.flashcard.FlashcardGenerateResponse;
import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.dto.flashcard.GenerateFlashcardsRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Flashcard;
import com.tugnw.aistudy.domain.mapper.FlashcardMapper;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FlashcardRepository;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.FlashcardService;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashcardServiceImpl implements FlashcardService {

    private final RagService ragService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardMapper flashcardMapper;
    private final KnowledgePreparationService knowledgePreparationService;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_FRONT_LENGTH = 500;
    private static final int MAX_BACK_LENGTH = 2000;
    private static final int MIN_DOCUMENT_TEXT_LENGTH = 200;

    @Override
    @Transactional
    public FlashcardGenerateResponse generateFlashcards(UUID documentId, UUID requesterId, GenerateFlashcardsRequest request) throws Exception {

        Document document = documentService.getAccessibleDocument(documentId, requesterId, true);

        if (!quotaService.checkQuota(requesterId, "flashcard"))
            throw new RuntimeException("Bạn đã đạt giới hạn số lần tạo flashcard cho gói hiện tại. Vui lòng nâng cấp gói để tiếp tục sử dụng.");

        String documentText = ragService.extractTextFromDocument(document.getId(), requesterId);

        if (documentText == null || documentText.isBlank()) throw new RuntimeException("Unable to extract text from document.");

        if (documentText.length() < MIN_DOCUMENT_TEXT_LENGTH)
            log.warn("[LOG - FLASHCARD] Document text too short ({} chars), quality may be poor", documentText.length());

        log.info("[LOG - FLASHCARD] Extracted text length: " + documentText.length());

        // Bước 1: Gọi AI trước — chưa xóa card cũ
        List<Flashcard> parsed = generateFlashcardsFromText(documentText, document.getId(), request.getNumberOfCards());

        // Bước 2: Validate từng card, skip card lỗi
        List<Flashcard> valid = validateFlashcards(parsed, document.getId());

        // Bước 3: 0 card hợp lệ → fail hoàn toàn, giữ nguyên card cũ
        if (valid.isEmpty()) {
            log.error("[LOG - FLASHCARD] All {} parsed flashcards failed validation.", parsed.size());
            throw new RuntimeException("AI không tạo được flashcard hợp lệ từ tài liệu này. Vui lòng thử lại.");
        }

        // Bước 4: Xóa card cũ — lúc này AI đã trả về kết quả OK
        List<Flashcard> existing = flashcardRepository.findByDocumentId(documentId);
        if (!existing.isEmpty()) {
            flashcardRepository.deleteByDocumentId(documentId);
            flashcardRepository.flush();
            log.info("[LOG - FLASHCARD] Deleted {} existing flashcards.", existing.size());
        }

        // Bước 5: Save card mới
        flashcardRepository.saveAll(valid);

        // Increment generation counter
        document.setFlashcardGenerations(document.getFlashcardGenerations() == null ? 1 : document.getFlashcardGenerations() + 1);
        documentRepository.save(document);

        String message = buildResultMessage(valid.size(), request.getNumberOfCards(), parsed.size());
        log.info("[LOG - FLASHCARD] {}", message);

        return FlashcardGenerateResponse.builder()
                .flashcards(flashcardMapper.toResponseList(valid))
                .requestedCount(request.getNumberOfCards())
                .rawCount(parsed.size())
                .savedCount(valid.size())
                .message(message)
                .build();
    }

    @Override
    public List<FlashcardResponse> getFlashcardsByDocument(UUID documentId, UUID requesterId) {
        log.info("[LOG - FLASHCARD] Fetching flashcards for document: " + documentId);

        Document document = documentService.getAccessibleDocument(documentId, requesterId, true);

        List<Flashcard> flashcards = flashcardRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        return flashcardMapper.toResponseList(flashcards);
    }

    private List<Flashcard> generateFlashcardsFromText(String documentText, UUID documentId, Integer numberOfCards) throws Exception {
        String prompt = String.format(
                "Based on the following document content, generate exactly %d flashcards in JSON format. " +
                "Each flashcard should have 'front' (question) and 'back' (answer) fields. " +
                "Return a valid JSON array with objects containing 'front' and 'back' keys only.\n\n" +
                "Document:\n%s\n\n" +
                "Return ONLY valid JSON array, no markdown formatting, no code blocks.",
                numberOfCards,
                documentText.substring(0, Math.min(3000, documentText.length())));
        String aiResponse = ragService.generateContent(prompt);
        return parseFlashcardsFromResponse(aiResponse, documentId);
    }

    private List<Flashcard> parseFlashcardsFromResponse(String jsonResponse, UUID documentId) throws Exception {
        List<Flashcard> flashcards = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String front = item.has("front") ? item.get("front").asText().trim() : "";
                    String back = item.has("back") ? item.get("back").asText().trim() : "";

                    if (!front.isBlank() && !back.isBlank()) {
                        Flashcard flashcard = Flashcard.builder()
                                .documentId(documentId)
                                .frontContent(front)
                                .backContent(back)
                                .generatedByAi(true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        flashcards.add(flashcard);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[LOG - FLASHCARD ERROR] Failed to parse flashcards: " + e.getMessage());
            throw new RuntimeException("Failed to parse AI-generated flashcards.", e);
        }
        return flashcards;
    }

    /**
     * Tier 2 validation: skip cards that fail quality checks.
     * Never throws — chỉ skip card lỗi, giữ lại card tốt.
     */
    private List<Flashcard> validateFlashcards(List<Flashcard> cards, UUID documentId) {
        List<Flashcard> valid = new ArrayList<>();

        for (Flashcard card : cards) {
            String front = card.getFrontContent();
            String back = card.getBackContent();
            boolean skip = false;
            String reason = null;

            if (front.length() > MAX_FRONT_LENGTH) {
                skip = true;
                reason = "front too long (" + front.length() + " > " + MAX_FRONT_LENGTH + ")";
            } else if (back.length() > MAX_BACK_LENGTH) {
                skip = true;
                reason = "back too long (" + back.length() + " > " + MAX_BACK_LENGTH + ")";
            } else if (front.equalsIgnoreCase(back)) {
                skip = true;
                reason = "front equals back";
            } else if (valid.stream().anyMatch(v -> v.getFrontContent().equalsIgnoreCase(front))) {
                skip = true;
                reason = "duplicate front in batch";
            }

            if (skip) {
                log.warn("[LOG - FLASHCARD] Skipped card: {} — front=\"{}\"", reason, truncate(front, 60));
            } else {
                valid.add(card);
            }
        }

        return valid;
    }

    private String buildResultMessage(int saved, int requested, int raw) {
        if (saved == requested) {
            return "Đã tạo thành công " + saved + " flashcard.";
        }
        if (saved < 1) {
            return "Không tạo được flashcard nào. Vui lòng thử lại.";
        }
        if (saved == raw) {
            return "Yêu cầu " + requested + " flashcard, AI tạo được " + raw + ". Đã lưu " + saved + " flashcard.";
        }
        if ((double) saved / requested < 0.3) {
            return "Chỉ tạo được " + saved + "/" + requested + " flashcard hợp lệ. Nội dung tài liệu có thể quá ngắn hoặc không phù hợp để tạo flashcard.";
        }
        return "Đã tạo " + saved + "/" + requested + " flashcard. "
                + (raw - saved) + " card bị lỗi format hoặc trùng lặp đã được bỏ qua.";
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
