package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.ai.SummaryRequest;
import com.tugnw.aistudy.domain.dto.ai.SummaryResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Summary", description = "AI-generated document summaries with caching")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class AiSummaryController {

    private final KnowledgePreparationService knowledgePreparationService;
    private final DocumentRepository documentRepository;

    @PostMapping("/summary")
    @Operation(summary = "Generate or regenerate AI summary", description = "Generate summary for a document. force=true regenerates and overwrites cache.")
    public ResponseEntity<ApiResponse<SummaryResponse>> generateSummary(
            @Valid @RequestBody SummaryRequest request,
            Authentication authentication) throws Exception {

        UUID requesterId = getCurrentUserId(authentication);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(request.getDocumentId())
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have permission to access this document");
        }

        String mergedMarkdown = knowledgePreparationService.prepareKnowledge(List.of(document), request.isForce());

        return ResponseEntity.ok(ApiResponse.success(
                "AI Summary generated successfully",
                new SummaryResponse(mergedMarkdown)
        ));
    }

    @GetMapping("/summary/{documentId}")
    @Operation(summary = "Get cached summary", description = "Returns cached AI summary without regenerating. Returns empty if none exists.")
    public ResponseEntity<ApiResponse<SummaryResponse>> getCachedSummary(
            @PathVariable UUID documentId,
            Authentication authentication) {

        UUID requesterId = getCurrentUserId(authentication);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have permission to access this document");
        }

        if (document.getSummary() == null || document.getSummary().trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    "No summary available. Use POST to generate one.",
                    new SummaryResponse("")
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Cached summary retrieved",
                new SummaryResponse(document.getSummary())
        ));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("User chưa đăng nhập");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }
        throw new RuntimeException("Không thể xác định user");
    }
}
