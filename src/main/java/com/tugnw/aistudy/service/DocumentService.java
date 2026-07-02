package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request);

    List<DocumentResponse> getDocumentsByOwner(UUID ownerId);

    List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId);

    DocumentResponse getDocumentById(UUID id, UUID ownerId);

    DocumentResponse getSharedDocumentById(UUID id, UUID requesterId);

    DocumentResponse updateDocument(UUID id, UUID ownerId, DocumentUpdateRequest request);

    void deleteDocument(UUID id, UUID ownerId);

    List<DocumentResponse> getTrashDocuments(UUID requesterId);

    void restoreDocument(UUID id, UUID requesterId);

    String getDocumentDownloadUrl(UUID id, UUID ownerId);

    String generateShareableLink(UUID id, UUID ownerId);

    boolean hasShareAccess(UUID documentId, UUID userId);
}