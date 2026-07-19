package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;
import com.tugnw.aistudy.service.QuizService;
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
@RequestMapping("/api/quizzes")
@Tag(name = "Quizzes", description = "AI-generated quizzes from documents")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    @Operation(summary = "Generate quiz", description = "Generate or retrieve a quiz for a document. Use force=true to regenerate.")
    public ResponseEntity<QuizResponse> generateQuiz(
            @Valid @RequestBody GenerateQuizRequest request,
            Authentication authentication) throws Exception {
        UUID requesterId = getCurrentUserId(authentication);
        QuizResponse response = quizService.generateQuiz(
                request.getDocumentId(),
                requesterId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get quizzes by document")
    public ResponseEntity<List<QuizResponse>> getQuizByDocument(
            @PathVariable UUID documentId,
            Authentication authentication) {
        UUID requesterId = getCurrentUserId(authentication);
        List<QuizResponse> responses = quizService.getQuizByDocument(
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
