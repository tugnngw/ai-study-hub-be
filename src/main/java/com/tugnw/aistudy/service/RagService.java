package com.tugnw.aistudy.service;
import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import java.util.UUID;


public interface RagService {
    String extractTextFromDocument(UUID documentId, UUID requesterId) throws Exception;
    void processAndSaveDocumentPipeline(UUID documentId, UUID requesterId) throws Exception;
    RagChatResponse chatWithFolderContext(RagChatRequest chatRequest, UUID requesterId) throws Exception;
    String getDocumentProcessingStatus(UUID documentId, UUID requesterId);
    String generateContent(String prompt);
}