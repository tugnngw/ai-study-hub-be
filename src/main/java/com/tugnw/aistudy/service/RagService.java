package com.tugnw.aistudy.service;
import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;


public interface RagService {
    // Hàm từ Giai đoạn 1
    String extractTextFromDocument(Long documentId) throws Exception;
    
    // Hàm mới cho Giai đoạn 2
    void processAndSaveDocumentPipeline(Long documentId) throws Exception;

    // Hàm mới cho Giai đoạn 3
    RagChatResponse chatWithFolderContext(RagChatRequest chatRequest) throws Exception;
}