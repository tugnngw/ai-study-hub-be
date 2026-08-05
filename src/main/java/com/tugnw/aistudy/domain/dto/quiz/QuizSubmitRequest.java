package com.tugnw.aistudy.domain.dto.quiz;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitRequest {
    @NotEmpty
    private List<Answer> answers;

    @Data
    public static class Answer {
        private String questionId;
        private String selectedAnswer; // "A"/"B"/"C"/"D"
    }
}
