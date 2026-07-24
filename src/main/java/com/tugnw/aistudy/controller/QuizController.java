package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.QuizService;
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
@RequestMapping("/api/quizzes")
@Tag(name = "Quizzes", description = "AI-generated quizzes from documents")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }


    @PostMapping("/generate")
    @Operation(summary = "Generate quiz", description = "Generate or retrieve a quiz for a document. Use force=true to regenerate.")
    public ApiResponse<QuizResponse> generateQuiz(
            @Valid @RequestBody GenerateQuizRequest request,
            Authentication authentication) throws Exception {
        QuizResponse response = quizService.generateQuiz(request.getDocumentId(), userId(authentication), request);
        return ApiResponse.success(response);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get quizzes by document")
    public ApiResponse<List<QuizResponse>> getQuizByDocument(
            @PathVariable UUID documentId,
            Authentication authentication) {
        List<QuizResponse> responses = quizService.getQuizByDocument(documentId, userId(authentication));
        return ApiResponse.success(responses);
    }
}
