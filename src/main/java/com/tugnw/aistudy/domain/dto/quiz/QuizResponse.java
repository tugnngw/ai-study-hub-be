package com.tugnw.aistudy.domain.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResponse {

    private Long id;
    private String title;
    private Boolean generatedByAi;
    private LocalDateTime createdAt;
    private List<QuestionResponse> questions;
}
