package com.tugnw.aistudy.domain.dto.rag;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class RagChatRequest {

    private UUID folderId;
    private UUID documentId;
    private List<UUID> documentIds;

    @NotBlank(message = "Câu hỏi không được để trống")
    private String question;
}