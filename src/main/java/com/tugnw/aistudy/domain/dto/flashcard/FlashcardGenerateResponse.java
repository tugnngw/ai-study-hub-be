package com.tugnw.aistudy.domain.dto.flashcard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@Schema(description = "Result of AI flashcard generation")
public class FlashcardGenerateResponse {

    @Schema(description = "Successfully saved flashcards")
    private List<FlashcardResponse> flashcards;

    @Schema(description = "Number of cards requested", example = "10")
    private int requestedCount;

    @Schema(description = "Number of cards returned by AI", example = "8")
    private int rawCount;

    @Schema(description = "Number of cards saved after validation", example = "7")
    private int savedCount;

    @Schema(description = "Human-readable result message", example = "Đã tạo thành công 7/10 flashcard.")
    private String message;
}
