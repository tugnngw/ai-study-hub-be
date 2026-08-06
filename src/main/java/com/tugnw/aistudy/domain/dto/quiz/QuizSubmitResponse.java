package com.tugnw.aistudy.domain.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {
    private int correctCount;
    private int totalQuestions;
    private int percentage;
    /** Kết quả từng câu — backend chấm, FE chỉ render. */
    private List<QuestionResult> questionResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResult {
        private String questionId;
        private String selectedAnswer;
        private String correctAnswer;
        private boolean correct;
    }
}
