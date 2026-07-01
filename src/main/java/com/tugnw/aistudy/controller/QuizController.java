package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;
import com.tugnw.aistudy.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quizzes")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    public ResponseEntity<QuizResponse> generateQuiz(
            @Valid @RequestBody GenerateQuizRequest request,
            Authentication authentication) throws Exception {
        UUID requesterId = getCurrentUserId(authentication);
        QuizResponse response = quizService.generateQuiz(
                requesterId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}")
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

