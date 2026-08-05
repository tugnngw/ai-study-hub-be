package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;
import com.tugnw.aistudy.domain.dto.quiz.QuizSubmitRequest;
import com.tugnw.aistudy.domain.dto.quiz.QuizSubmitResponse;

import java.util.List;
import java.util.UUID;

public interface QuizService {

    QuizResponse generateQuiz(UUID documentId, UUID requesterId, GenerateQuizRequest request) throws Exception;

    List<QuizResponse> getQuizByDocument(UUID documentId, UUID requesterId);

    QuizSubmitResponse submitQuiz(UUID quizId, QuizSubmitRequest request);
}
