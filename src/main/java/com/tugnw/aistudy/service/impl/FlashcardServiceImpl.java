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
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.service.DocumentSourceResolver;
import com.tugnw.aistudy.service.FlashcardService;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
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
    private final DocumentSourceResolver documentSourceResolver;
    private final KnowledgePreparationService knowledgePreparationService;
    private final FolderRepository folderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public List<FlashcardResponse> generateFlashcards(UUID requesterId, GenerateFlashcardsRequest request) throws Exception {
        System.out.println("[LOG - FLASHCARD] Starting flashcard generation");

        // Validate request
        validateRequest(request);

        // Resolve source documents
        List<Document> sourceDocuments = resolveSourceDocuments(requesterId, request);

        if (sourceDocuments.isEmpty()) {
            throw new RuntimeException("No accessible documents found for generation");
        }

        System.out.println("[LOG - FLASHCARD] Resolved " + sourceDocuments.size() + " documents");

        // Prepare knowledge using summary-based pipeline
        String mergedContent = knowledgePreparationService.prepareKnowledge(sourceDocuments, false);

        System.out.println("[LOG - FLASHCARD] Knowledge prepared: " + mergedContent.length() + " chars");

        // Generate flashcards from merged content
        List<Flashcard> generatedFlashcards = generateFlashcardsFromText(
            mergedContent,
            sourceDocuments.get(0).getId(),
            request.getNumberOfCards()
        );

        flashcardRepository.saveAll(generatedFlashcards);

        System.out.println("[LOG - FLASHCARD] Successfully generated and saved " + generatedFlashcards.size() + " flashcards");

        return flashcardMapper.toResponseList(generatedFlashcards);
    }

    @Override
    public List<FlashcardResponse> getFlashcardsByDocument(UUID documentId, UUID requesterId) {
        System.out.println("[LOG - FLASHCARD] Fetching flashcards for document: " + documentId);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted"));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("You do not have permission to access flashcards for this document");
        }

        List<Flashcard> flashcards = flashcardRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        return flashcardMapper.toResponseList(flashcards);
    }

    private void validateRequest(GenerateFlashcardsRequest request) {
        boolean hasDocIds = request.getDocumentIds() != null && !request.getDocumentIds().isEmpty();
        boolean hasOldDocId = request.getDocumentId() != null;
        boolean hasFolderId = request.getFolderId() != null;

        // Must have at least one source
        if (!hasDocIds && !hasOldDocId && !hasFolderId) {
            throw new RuntimeException("Must specify documentId, documentIds, or folderId");
        }

        // Cannot mix folder with document IDs
        if (hasFolderId && (hasDocIds || hasOldDocId)) {
            throw new RuntimeException("Cannot specify both folder and document IDs");
        }

        // If folder, includeAllDocuments must be true
        if (hasFolderId && !request.isIncludeAllDocuments()) {
            throw new RuntimeException("If folderId is specified, includeAllDocuments must be true");
        }
    }

    private List<Document> resolveSourceDocuments(UUID requesterId, GenerateFlashcardsRequest request) {
        List<Document> documents = new ArrayList<>();

        // Priority: documentIds (new) > documentId (old) > folderId
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            documents = documentSourceResolver.resolveByDocumentIds(request.getDocumentIds());
        } else if (request.getDocumentId() != null) {
            documents = documentSourceResolver.resolveByDocumentIds(List.of(request.getDocumentId()));
        } else if (request.getFolderId() != null) {
            documents = documentSourceResolver.resolveByFolderId(request.getFolderId());
        }

        // Authorize all resolved documents
        authorizeDocuments(documents, requesterId, request.getFolderId());

        return documents;
    }

    private void authorizeDocuments(List<Document> documents, UUID requesterId, UUID folderId) {
        // If folderId was used, verify folder ownership first
        if (folderId != null) {
            folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .ifPresent(folder -> {
                    if (!isAdmin() && !folder.getOwnerId().equals(requesterId)) {
                        throw new RuntimeException("Access denied to folder: " + folderId);
                    }
                });
        }

        // Verify ownership of each document
        for (Document doc : documents) {
            if (!isAdmin() && !doc.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("Access denied to document: " + doc.getId());
            }
        }
    }

    private List<Flashcard> generateFlashcardsFromText(String mergedContent, UUID primaryDocId, Integer numberOfCards) throws Exception {
        String prompt = String.format(
                "Based on the following document content, generate exactly %d flashcards in JSON format. " +
                "Each flashcard should have 'front' (question) and 'back' (answer) fields. " +
                "Return a valid JSON array with objects containing 'front' and 'back' keys only.\n\n" +
                "Document:\n%s\n\n" +
                "Return ONLY valid JSON array, no markdown formatting, no code blocks.",
                numberOfCards,
                mergedContent.substring(0, Math.min(3000, mergedContent.length()))
        );

        String aiResponse = ragService.generateContent(prompt);
        System.out.println("[LOG - FLASHCARD] Gemini response received, parsing flashcards...");

        return parseFlashcardsFromResponse(aiResponse, primaryDocId);
    }

    private List<Flashcard> parseFlashcardsFromResponse(String jsonResponse, UUID primaryDocId) throws Exception {
        List<Flashcard> flashcards = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String front = item.has("front") ? item.get("front").asText().trim() : "";
                    String back = item.has("back") ? item.get("back").asText().trim() : "";

                    if (!front.isBlank() && !back.isBlank()) {
                        Flashcard flashcard = Flashcard.builder()
                                .documentId(primaryDocId)
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
            throw new RuntimeException("Failed to parse AI-generated flashcards", e);
        }
        return flashcards;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
