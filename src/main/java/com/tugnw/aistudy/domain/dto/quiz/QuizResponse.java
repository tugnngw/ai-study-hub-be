package com.tugnw.aistudy.domain.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Quiz with questions")
public class QuizResponse {

    @Schema(description = "Quiz ID", example = "d4e5f6a7-...")
    private UUID id;

    @Schema(description = "Quiz title", example = "AI-Generated Quiz")
    private String title;

    @Schema(description = "Whether AI-generated", example = "true")
    private Boolean generatedByAi;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "List of questions")
    private List<QuestionResponse> questions;
}
