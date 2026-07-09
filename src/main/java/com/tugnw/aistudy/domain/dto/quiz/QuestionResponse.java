package com.tugnw.aistudy.domain.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Quiz question with multiple-choice options")
public class QuestionResponse {

    @Schema(description = "Question ID", example = "1")
    private Long id;

    @Schema(description = "Question content", example = "What is the time complexity of binary search?")
    private String content;

    @Schema(description = "Option A", example = "O(n)")
    private String optionA;

    @Schema(description = "Option B", example = "O(log n)")
    private String optionB;

    @Schema(description = "Option C", example = "O(n^2)")
    private String optionC;

    @Schema(description = "Option D", example = "O(1)")
    private String optionD;

    @Schema(description = "Correct answer (A/B/C/D)", example = "B")
    private String correctAnswer;
}
