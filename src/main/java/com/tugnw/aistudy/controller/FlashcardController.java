package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.flashcard.FlashcardGenerateResponse;
import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.dto.flashcard.GenerateFlashcardsRequest;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.FlashcardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flashcards")
@Tag(name = "Flashcards", description = "AI-generated flashcards from documents")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @PostMapping("/generate")
    @Operation(summary = "Generate flashcards", description = "Generate or retrieve flashcards for a document. Use force=true to regenerate.")
    public ApiResponse<FlashcardGenerateResponse> generateFlashcards(
            @Valid @RequestBody GenerateFlashcardsRequest request, Authentication authentication) throws Exception {
        FlashcardGenerateResponse response = flashcardService.generateFlashcards(
                request.getDocumentId(), userId(authentication), request);
        return ApiResponse.success("Flashcards generated successfully", response);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get flashcards by document")
    public ApiResponse<List<FlashcardResponse>> getFlashcardsByDocument(
            @PathVariable UUID documentId, Authentication authentication) {
        List<FlashcardResponse> responses = flashcardService.getFlashcardsByDocument(
                documentId, userId(authentication));
        return ApiResponse.success(responses);
    }
}
