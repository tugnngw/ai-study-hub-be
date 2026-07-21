package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.entity.Document;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request);

    List<DocumentResponse> getDocumentsByOwner(UUID ownerId);

    List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId);

    List<DocumentResponse> getSharedFolderDocuments(UUID userId, UUID folderId);

    DocumentResponse getDocumentById(UUID id, UUID ownerId);

    DocumentResponse getSharedDocumentById(UUID id, UUID requesterId);

    DocumentResponse updateDocument(UUID id, UUID ownerId, DocumentUpdateRequest request);

    void deleteDocument(UUID id, UUID ownerId);

    void permanentDeleteDocument(UUID id, UUID requesterId);

    List<DocumentResponse> getTrashDocuments(UUID requesterId);

    void restoreDocument(UUID id, UUID requesterId);

    String getDocumentDownloadUrl(UUID id, UUID ownerId);

    String generateShareableLink(UUID id, UUID ownerId);

    boolean hasShareAccess(UUID documentId, UUID userId);

    /**
     * Find a document and verify the requester has access.
     * For AI operations (aiRequired=true): only READY documents are accessible
     *   unless requester is admin/owner.
     * For view operations (aiRequired=false): owner/admin can access any status;
     *   shared users can only access READY documents.
     *
     * @return the document if accessible
     * @throws AccessDeniedException if the requester has no permission
     * @throws RuntimeException if the document is not found or soft-deleted
     */
    Document getAccessibleDocument(UUID documentId, UUID requesterId, boolean aiRequired);
}