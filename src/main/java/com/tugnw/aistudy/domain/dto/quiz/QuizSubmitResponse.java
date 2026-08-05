package com.tugnw.aistudy.domain.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {
    private int correctCount;
    private int totalQuestions;
    private int percentage;
}
