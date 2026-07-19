package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.dto.flashcard.GenerateFlashcardsRequest;
import com.tugnw.aistudy.service.FlashcardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/generate")
    @Operation(summary = "Generate flashcards", description = "Generate or retrieve flashcards for a document. Use force=true to regenerate.")
    public ResponseEntity<List<FlashcardResponse>> generateFlashcards(
            @Valid @RequestBody GenerateFlashcardsRequest request,
            Authentication authentication) throws Exception {
        UUID requesterId = getCurrentUserId(authentication);
        List<FlashcardResponse> responses = flashcardService.generateFlashcards(
                request.getDocumentId(),
                requesterId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get flashcards by document")
    public ResponseEntity<List<FlashcardResponse>> getFlashcardsByDocument(
            @PathVariable UUID documentId,
            Authentication authentication) {
        UUID requesterId = getCurrentUserId(authentication);
        List<FlashcardResponse> responses = flashcardService.getFlashcardsByDocument(
                documentId,
                requesterId
        );
        return ResponseEntity.ok(responses);
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
