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
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.CloudinaryService;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.repository.ChatSessionRepository;

import com.tugnw.aistudy.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
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
@Slf4j
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final ShareRepository shareRepository;
    private final FolderRepository folderRepository;
    private final DocumentMapper documentMapper;
    private final CloudinaryService cloudinaryService;
    private final ActivityLogService activityLogService;
    private final AccountRepository accountRepository;
    private final QuotaService quotaService;
    private final ChatSessionRepository chatSessionRepository;

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
        Document document = getAccessibleDocument(id, requesterId, false);
        return documentMapper.toResponse(document);
    }

    @Override
    public List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request) {
        List<DocumentResponse> responses = new ArrayList<>();

        long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB
        long totalIncoming = 0;
        for (MultipartFile file : request.getFiles()) {
            totalIncoming += file.getSize();
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new RuntimeException("File size exceeds limit (50MB)");
            }
        }

        // Check storage quota from active subscription or free plan
        QuotaService.QuotaDetails quota = quotaService.getQuotaDetails(ownerId);
        Double storageLimitGb = quota.getStorageGb();
        if (storageLimitGb != null) {
            long storageLimitBytes = (long) (storageLimitGb * 1024L * 1024L * 1024L);
            long usedBytes = documentRepository.sumFileSizeByOwnerId(ownerId);
            if (usedBytes + totalIncoming > storageLimitBytes) {
                log.warn("Storage limit exceeded for user {}: used={}, limit={}, incoming={}",
                    ownerId, formatBytes(usedBytes), formatBytes(storageLimitBytes), formatBytes(totalIncoming));
                throw new RuntimeException(
                    "Bạn đã sử dụng hết dung lượng lưu trữ (" + formatBytes(usedBytes) + "/" + formatBytes(storageLimitBytes) + "). "
                    + "Vui lòng nâng cấp gói Premium để có thêm không gian."
                );
            }
        }

        for (MultipartFile file : request.getFiles()) {

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
            
            // Populate subjectId from folder
            if (request.getFolderId() != null) {
                var folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
                if (folder.getSubject() != null) {
                    document.setSubjectId(folder.getSubject().getId());
                }
            }

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

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByOwner(UUID ownerId) {
        return documentRepository
                .findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(documentMapper::toResponse)
                .map(this::sanitizeForListing)
                .toList();
    }

    /** Strip file URL from non-READY docs so listing doesn't leak content. */
    private DocumentResponse sanitizeForListing(DocumentResponse resp) {
        if (!"READY".equalsIgnoreCase(resp.getStatus())) {
            resp.setCloudinaryUrl(null);
        }
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId) {
        List<Document> documents = documentRepository
                .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);

        // Only return documents the user has access to
        return documents.stream()
                .filter(d -> {
                    try {
                        getAccessibleDocument(d.getId(), ownerId, false);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(documentMapper::toResponse)
                .map(this::sanitizeForListing)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getSharedFolderDocuments(UUID userId, UUID folderId) {
        boolean hasFolderShareAccess = shareRepository.existsByFolderIdAndSharedAccountIdAndRevokedFalse(folderId, userId);
        if (!hasFolderShareAccess && !isAdmin()) {
            throw new AccessDeniedException("You do not have permission to access this shared folder");
        }

        List<Document> documents = documentRepository
                .findByFolderIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(folderId, "READY");

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID id, UUID ownerId) {
        Document document = getAccessibleDocument(id, ownerId, false);
        DocumentResponse resp = documentMapper.toResponse(document);
        // Strip file URL for non-READY docs — content is inaccessible.
        if (!"READY".equalsIgnoreCase(resp.getStatus())) {
            resp.setCloudinaryUrl(null);
        }
        return resp;
    }

    @Override
    public DocumentResponse updateDocument(UUID id, UUID ownerId, DocumentUpdateRequest request) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to update this document");
        }

        if (request.getTitle() != null) document.setTitle(request.getTitle());
        if (request.getDescription() != null) document.setDescription(request.getDescription());
        if (request.getFolderId() != null) document.setFolderId(request.getFolderId());

        Document updatedDocument = documentRepository.save(document);
        return documentMapper.toResponse(updatedDocument);
    }

    @Override
    public void deleteDocument(UUID id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to delete this document");
        }

        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Override
    public void permanentDeleteDocument(UUID id, UUID requesterId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        // 1) Must already be in trash (soft-deleted first)
        if (document.getDeletedAt() == null) {
            throw new IllegalArgumentException("Document must be in trash before permanent deletion");
        }

        boolean isOwner = document.getOwnerId().equals(requesterId);
        if (!isOwner && !isAdmin()) {
            throw new AccessDeniedException("You do not have permission to permanently delete this document");
        }

        // 2) Delete external resources BEFORE removing the DB row.
        //    If Cloudinary fails, exception propagates, DB untouched.
        if (document.getPublicId() != null) {
            cloudinaryService.delete(document.getPublicId());
        }

        // 3) Clean up AI resources owned by RagService.
        chatSessionRepository.deleteByDocumentId(id);

        // 4) DB delete — all child tables with ON DELETE CASCADE
        //    (document_chunk, flashcard, quiz, share, report, bookmark,
        //     study_report) are cleaned automatically.
        documentRepository.delete(document);
        log.info("Permanently deleted document {} by user {}", id, requesterId);
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
            throw new AccessDeniedException("You do not have permission to restore this document");
        }

        document.setDeletedAt(null);
        documentRepository.save(document);
    }

    @Override
    public String getDocumentDownloadUrl(UUID id, UUID ownerId) {
        Document document = getAccessibleDocument(id, ownerId, true);

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

    @Override
    public Document getAccessibleDocument(UUID documentId, UUID requesterId, boolean aiRequired) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        boolean isOwner = document.getOwnerId().equals(requesterId);
        boolean isShared = hasShareAccess(documentId, requesterId);
        boolean isAdmin = isAdmin();

        // Admin always bypasses status checks
        if (isAdmin) {
            return document;
        }

        // Owner always bypasses status checks for view/manage operations
        if (isOwner && !aiRequired) {
            return document;
        }

        // Owner: AI operations require READY status
        if (isOwner) {
            if (!"READY".equalsIgnoreCase(document.getStatus())) {
                throw new AccessDeniedException(
                    "Tài liệu chưa được phê duyệt. Vui lòng đợi quản trị viên xét duyệt."
                );
            }
            return document;
        }

        // Shared user: must have share access AND document must be READY
        if (isShared) {
            if (!"READY".equalsIgnoreCase(document.getStatus())) {
                throw new AccessDeniedException("Tài liệu chưa được phê duyệt.");
            }
            return document;
        }

        throw new AccessDeniedException("You do not have permission to access this document");
    }
}