package com.tugnw.aistudy.domain.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class RagChatRequest {

    private UUID sessionId;

    @NotNull(message = "Document ID must not be null")
    private UUID documentId;

    @NotBlank(message = "Câu hỏi không được để trống")
    private String question;
}
