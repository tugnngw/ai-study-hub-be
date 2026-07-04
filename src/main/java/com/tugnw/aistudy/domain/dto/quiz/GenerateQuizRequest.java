package com.tugnw.aistudy.domain.dto.quiz;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class GenerateQuizRequest {

    @NotEmpty(message = "Document IDs must not be empty")
    private List<UUID> documentIds;

    @Min(value = 1, message = "Number of questions must be at least 1")
    private Integer numberOfQuestions;
}
