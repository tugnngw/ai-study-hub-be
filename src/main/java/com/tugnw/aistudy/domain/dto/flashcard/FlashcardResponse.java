package com.tugnw.aistudy.domain.dto.flashcard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Flashcard information")
public class FlashcardResponse {

    @Schema(description = "Flashcard ID", example = "c3d4e5f6-...")
    private UUID id;

    @Schema(description = "Front content (question)", example = "What is polymorphism?")
    private String frontContent;

    @Schema(description = "Back content (answer)", example = "The ability of objects to take multiple forms...")
    private String backContent;

    @Schema(description = "Whether AI-generated", example = "true")
    private Boolean generatedByAi;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
}
