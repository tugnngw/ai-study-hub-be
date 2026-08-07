package com.tugnw.aistudy.domain.dto.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Request to generate a quiz from a document")
public class GenerateQuizRequest {

    @NotNull(message = "Document ID must not be null")
    @Schema(description = "Document ID to generate from", example = "a1b2c3d4-...", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID documentId;

    @Min(value = 1, message = "Number of questions must be at least 1")
    @Max(value = 20, message = "Number of questions must not exceed 20")
    @Schema(description = "Number of questions to generate", example = "5", minimum = "1", maximum = "20")
    private Integer numberOfQuestions;
}
