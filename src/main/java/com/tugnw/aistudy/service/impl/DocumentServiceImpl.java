package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.mapper.DocumentMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.CloudinaryService;
import com.tugnw.aistudy.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final ShareRepository shareRepository;
    private final DocumentMapper documentMapper;
    private final CloudinaryService cloudinaryService;
    private final ActivityLogService activityLogService;
    private final AccountRepository accountRepository;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasShareAccess(UUID documentId, UUID userId) {
        return shareRepository.existsByDocumentIdAndSharedAccountIdAndRevokedFalse(documentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getSharedDocumentById(UUID id, UUID requesterId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId) && !hasShareAccess(id, requesterId)) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        return documentMapper.toResponse(document);
    }

    @Override
    public List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request) {
        List<DocumentResponse> responses = new ArrayList<>();

        for (MultipartFile file : request.getFiles()) {
            // Validate file size (max 50MB)
            long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new RuntimeException("File size exceeds limit (50MB)");
            }

            // Validate file type
            String contentType = file.getContentType();
            boolean allowedType = contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                contentType.equals("text/plain") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            );
            if (!allowedType) {
                throw new RuntimeException("Invalid file type. Only PDF, DOCX, TXT, PPTX are allowed");
            }

            // Upload file to Cloudinary
            var uploadResult = cloudinaryService.upload(file);

            Document document = documentMapper.toEntity(request);
            document.setOwnerId(ownerId);
            document.setCloudinaryUrl((String) uploadResult.get("secure_url"));
            document.setPublicId((String) uploadResult.get("public_id"));
            document.setMimeType(contentType);
            document.setFileSize(file.getSize());
            document.setStatus("COMPLETED");

            Document savedDocument = documentRepository.save(document);

            // Log upload activity
            Account owner = accountRepository.findById(ownerId).orElse(null);
            if (owner != null) {
                activityLogService.logActivity(
                        ownerId,
                        owner.getUsername(),
                        ActivityType.DOCUMENT_UPLOAD,
                        "Uploaded document: " + savedDocument.getTitle()
                );
            }

            // Add to responses
            responses.add(documentMapper.toResponse(savedDocument));
            

        }

        // Return all responses
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByOwner(UUID ownerId) {
        List<Document> documents = documentRepository
                .findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId) {
        // TODO: Check owner has permission to access this folder
        List<Document> documents = documentRepository
                .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId) && !hasShareAccess(id, ownerId)) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        return documentMapper.toResponse(document);
    }

    @Override
    public DocumentResponse updateDocument(UUID id, UUID ownerId, DocumentUpdateRequest request) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You do not have permission to update this document");
        }

        if (request.getTitle() != null) document.setTitle(request.getTitle());
        if (request.getDescription() != null) document.setDescription(request.getDescription());
        if (request.getFolderId() != null) document.setFolderId(request.getFolderId());
        if (request.getSubjectId() != null) document.setSubjectId(request.getSubjectId());

        Document updatedDocument = documentRepository.save(document);
        return documentMapper.toResponse(updatedDocument);
    }

    @Override
    public void deleteDocument(UUID id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You do not have permission to delete this document");
        }

        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Override
    public List<DocumentResponse> getTrashDocuments(UUID requesterId) {
        List<Document> docs = isAdmin()
            ? documentRepository.findByDeletedAtIsNotNullOrderByCreatedAtDesc()
            : documentRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByCreatedAtDesc(requesterId);
        return docs.stream().map(documentMapper::toResponse).toList();
    }

    @Override
    public void restoreDocument(UUID id, UUID requesterId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getDeletedAt() == null) {
            throw new RuntimeException("Document is not in trash");
        }

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("You do not have permission to restore this document");
        }

        document.setDeletedAt(null);
        documentRepository.save(document);
    }

    @Override
    public String getDocumentDownloadUrl(UUID id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId) && !hasShareAccess(id, ownerId)) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        // Log download activity
        Account owner = accountRepository.findById(ownerId).orElse(null);
        if (owner != null) {
            activityLogService.logActivity(
                    ownerId,
                    owner.getUsername(),
                    ActivityType.DOCUMENT_DOWNLOAD,
                    "Downloaded document: " + document.getTitle()
            );
        }

        return document.getCloudinaryUrl();
    }

    @Override
    public String generateShareableLink(UUID id, UUID ownerId) {
        // TODO: Implement shareable link logic
        return null;
    }
}