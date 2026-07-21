package com.tugnw.aistudy.domain.dto.flashcard;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Request to generate flashcards from a document")
public class GenerateFlashcardsRequest {

    @NotNull(message = "Document ID must not be null")
    @Schema(description = "Document ID to generate from", example = "a1b2c3d4-...", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID documentId;

    @Min(value = 1, message = "Number of cards must be at least 1")
    @Max(value = 50, message = "Number of cards must not exceed 50")
    @Schema(description = "Number of flashcards to generate", example = "10", minimum = "1", maximum = "50")
    private Integer numberOfCards;
}
