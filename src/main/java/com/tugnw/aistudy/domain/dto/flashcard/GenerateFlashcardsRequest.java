package com.tugnw.aistudy.domain.dto.flashcard;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.UUID;

@Data
public class GenerateFlashcardsRequest {

    @NotNull(message = "Document ID cannot be null")
    private UUID documentId;

    @Min(value = 1, message = "Number of cards must be at least 1")
    private Integer numberOfCards;
}
