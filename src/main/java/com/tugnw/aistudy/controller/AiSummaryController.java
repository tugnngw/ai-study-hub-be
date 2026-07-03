package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.ai.SummaryRequest;
import com.tugnw.aistudy.domain.dto.ai.SummaryResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.service.DocumentSourceResolver;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class AiSummaryController {

    private final KnowledgePreparationService knowledgePreparationService;
    private final DocumentSourceResolver documentSourceResolver;

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> generateSummary(
            @Valid @RequestBody SummaryRequest request,
            Authentication authentication) throws Exception {

        UUID requesterId = getCurrentUserId(authentication);

        List<Document> documents = documentSourceResolver.resolveByDocumentIds(request.getDocumentIds());
        if (documents.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No accessible documents found for the given IDs"));
        }

        authorizeDocuments(documents, requesterId);

        String mergedMarkdown = knowledgePreparationService.prepareKnowledge(documents, request.isForce());

        return ResponseEntity.ok(ApiResponse.success(
                "AI Summary generated successfully",
                new SummaryResponse(mergedMarkdown)
        ));
    }

    private void authorizeDocuments(List<Document> documents, UUID requesterId) {
        for (Document doc : documents) {
            if (!isAdmin() && !doc.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("Access denied to document: " + doc.getId());
            }
        }
    }

    private boolean isAdmin() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
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
