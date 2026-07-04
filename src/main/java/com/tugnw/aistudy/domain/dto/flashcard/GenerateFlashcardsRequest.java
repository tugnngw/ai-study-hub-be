package com.tugnw.aistudy.domain.dto.flashcard;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class GenerateFlashcardsRequest {

    @NotEmpty(message = "Document IDs must not be empty")
    private List<UUID> documentIds;

    @Min(value = 1, message = "Number of cards must be at least 1")
    private Integer numberOfCards;
}
