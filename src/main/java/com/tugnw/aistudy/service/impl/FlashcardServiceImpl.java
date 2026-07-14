package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.dto.flashcard.GenerateFlashcardsRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Flashcard;
import com.tugnw.aistudy.domain.mapper.FlashcardMapper;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FlashcardRepository;
import com.tugnw.aistudy.service.FlashcardService;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlashcardServiceImpl implements FlashcardService {

    private final RagService ragService;
    private final DocumentRepository documentRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardMapper flashcardMapper;
    private final KnowledgePreparationService knowledgePreparationService;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public List<FlashcardResponse> generateFlashcards(UUID documentId, UUID requesterId, GenerateFlashcardsRequest request) throws Exception {
        System.out.println("[LOG - FLASHCARD] Starting flashcard generation for document: " + documentId);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have permission to access this document");
        }

        // Check quota before generating flashcards
        if (!quotaService.checkQuota(requesterId, "flashcard")) {
            throw new RuntimeException("Bạn đã đạt giới hạn số lượng flashcard cho gói hiện tại. Vui lòng nâng cấp gói để tiếp tục sử dụng.");
        }

        // Always regenerate — delete existing, create fresh
        List<Flashcard> existing = flashcardRepository.findByDocumentId(documentId);
        if (!existing.isEmpty()) {
            flashcardRepository.deleteByDocumentId(documentId);
            flashcardRepository.flush();
            System.out.println("[LOG - FLASHCARD] Deleted " + existing.size() + " existing flashcards.");
        }

        String documentText = knowledgePreparationService.prepareKnowledge(List.of(document), false);

        if (documentText == null || documentText.isBlank()) {
            throw new RuntimeException("Unable to extract text from document.");
        }

        System.out.println("[LOG - FLASHCARD] Extracted text length: " + documentText.length());

        List<Flashcard> generatedFlashcards = generateFlashcardsFromText(documentText, document.getId(), request.getNumberOfCards());
        flashcardRepository.saveAll(generatedFlashcards);

        System.out.println("[LOG - FLASHCARD] Successfully generated and saved " + generatedFlashcards.size() + " flashcards.");

        return flashcardMapper.toResponseList(generatedFlashcards);
    }

    @Override
    public List<FlashcardResponse> getFlashcardsByDocument(UUID documentId, UUID requesterId) {
        System.out.println("[LOG - FLASHCARD] Fetching flashcards for document: " + documentId);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have permission to access flashcards for this document.");
        }

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
                documentText.substring(0, Math.min(3000, documentText.length()))
        );

        String aiResponse = ragService.generateContent(prompt);
        System.out.println("[LOG - FLASHCARD] Gemini response received, parsing flashcards...");

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
            System.err.println("[LOG - FLASHCARD ERROR] Failed to parse flashcards: " + e.getMessage());
            throw new RuntimeException("Failed to parse AI-generated flashcards.", e);
        }
        return flashcards;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
