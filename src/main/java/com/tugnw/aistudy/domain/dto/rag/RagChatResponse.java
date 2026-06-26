package com.tugnw.aistudy.domain.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
public class RagChatResponse {
    private String answer;
    private Set<UUID> referencedDocumentIds; // Danh sách ID tài liệu AI đã đọc để trả lời
}