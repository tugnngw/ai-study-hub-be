package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;

import java.util.List;
import java.util.UUID;

public interface QuizService {

    QuizResponse generateQuiz(List<UUID> documentIds, UUID requesterId, GenerateQuizRequest request) throws Exception;

    List<QuizResponse> getQuizByDocument(UUID documentId, UUID requesterId);
}
