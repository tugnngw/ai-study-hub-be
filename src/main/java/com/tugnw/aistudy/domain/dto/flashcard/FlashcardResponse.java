package com.tugnw.aistudy.domain.dto.flashcard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardResponse {

    private Long id;
    private String frontContent;
    private String backContent;
    private Boolean generatedByAi;
    private LocalDateTime createdAt;
}
