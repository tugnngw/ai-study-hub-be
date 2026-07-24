package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.ai.SummaryRequest;
import com.tugnw.aistudy.domain.dto.ai.SummaryResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Summary", description = "AI-generated document summaries with caching")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class AiSummaryController {

    private final KnowledgePreparationService knowledgePreparationService;
    private final DocumentService documentService;

    @PostMapping("/summary")
    @Operation(summary = "Generate or regenerate AI summary", description = "Generate summary for a document. force=true regenerates and overwrites cache.")
    public ApiResponse<SummaryResponse> generateSummary(
            @Valid @RequestBody SummaryRequest request,
            Authentication authentication) throws Exception {

        Document document = documentService.getAccessibleDocument(request.getDocumentId(),
                ((CustomUserDetails) authentication.getPrincipal()).getAccount().getId(), true);

        String mergedMarkdown = knowledgePreparationService.prepareKnowledge(document, request.isForce());

        return ApiResponse.success(
                "AI Summary generated successfully",
                new SummaryResponse(mergedMarkdown)
        );
    }

    @GetMapping("/summary/{documentId}")
    @Operation(summary = "Get cached summary", description = "Returns cached AI summary without regenerating. Returns empty if none exists.")
    public ApiResponse<SummaryResponse> getCachedSummary(
            @PathVariable UUID documentId,
            Authentication authentication) {

        Document document = documentService.getAccessibleDocument(documentId,
                ((CustomUserDetails) authentication.getPrincipal()).getAccount().getId(), true);

        if (document.getSummary() == null || document.getSummary().trim().isEmpty())
            return ApiResponse.success(
                    "No summary available. Use POST to generate one.",
                    new SummaryResponse("")
            );

        return ApiResponse.success(
                "Cached summary retrieved",
                new SummaryResponse(document.getSummary())
        );
    }
}
