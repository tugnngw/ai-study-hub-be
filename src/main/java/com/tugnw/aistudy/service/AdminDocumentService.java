package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import java.util.List;
import java.util.UUID;

public interface AdminDocumentService {
    List<DocumentResponse> getAllDocuments();
    List<DocumentResponse> getDocumentsByStatus(String status);
    List<DocumentResponse> getTrashDocuments();
    void deleteDocument(UUID id);
    void restoreDocument(UUID id, UUID adminId, String adminName);
    void approveDocument(UUID id);
    void rejectDocument(UUID id, String reason);
}