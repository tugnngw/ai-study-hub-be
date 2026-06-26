package com.tugnw.aistudy.service;
import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import java.util.UUID;


public interface RagService {
    String extractTextFromDocument(UUID documentId) throws Exception;
    void processAndSaveDocumentPipeline(UUID documentId) throws Exception;
    RagChatResponse chatWithFolderContext(RagChatRequest chatRequest) throws Exception;
    void processFolderPipeline(UUID folderId) throws Exception;
    String getDocumentProcessingStatus(UUID documentId);
}