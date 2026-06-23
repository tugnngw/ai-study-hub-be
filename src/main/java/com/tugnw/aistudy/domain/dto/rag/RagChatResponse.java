package com.tugnw.aistudy.domain.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Set;

@Data
@AllArgsConstructor
public class RagChatResponse {
    private String answer;
    private Set<Long> referencedDocumentIds; // Danh sách ID tài liệu AI đã đọc để trả lời
}