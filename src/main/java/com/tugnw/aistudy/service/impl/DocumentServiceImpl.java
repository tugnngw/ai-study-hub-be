package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.DocumentStatus;
import com.tugnw.aistudy.domain.mapper.DocumentMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.CloudinaryService;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.StorageQuotaService;
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
    private final ChatSessionRepository chatSessionRepository;
    private final StorageQuotaService storageQuotaService;

    @Override
    public List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request) {
        List<DocumentResponse> responses = new ArrayList<>();

        long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
        long totalIncoming = 0;
        for (MultipartFile file : request.getFiles()) {
            totalIncoming += file.getSize();
            if (file.getSize() > MAX_FILE_SIZE)
                throw new RuntimeException("File size exceeds limit (5MB)");
        }

        // Storage quota — reserve lock account + cộng used ngay (atomic theo account,
        // rollback tự trả lại nếu upload sau đó thất bại).
        storageQuotaService.reserveStorage(ownerId, totalIncoming);

        for (MultipartFile file : request.getFiles()) {

            // Validate file type
            String contentType = file.getContentType();
            boolean allowedType = contentType != null && (
                contentType.equals("application/pdf") ||
                contentType.equals("text/plain")
            );
            if (!allowedType)
                throw new RuntimeException("Invalid file type. Only PDF, TXT are allowed");

            // Upload file to Cloudinary
            var uploadResult = cloudinaryService.upload(file);

            Document document = documentMapper.toEntity(request);
            document.setOwnerId(ownerId);
            
            // Populate subjectId from folder
            if (request.getFolderId() != null) {
                var folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
                if (folder.getSubject() != null)
                    document.setSubjectId(folder.getSubject().getId());
            }

            document.setCloudinaryUrl((String) uploadResult.get("secure_url"));
            document.setPublicId((String) uploadResult.get("public_id"));
            document.setMimeType(contentType);
            document.setFileSize(file.getSize());
            document.setStatus("COMPLETED");

            Document savedDocument = documentRepository.save(document);

            // usedStorageBytes đã được cộng tại reserveStorage — không cộng lại ở đây.

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

/** BANNED vẫn hiện metadata trong listing (owner cần thấy để xóa + appeal tương lai).
     *  Mọi action (view/download/AI/share/edit) bị chặn ở getAccessibleDocument/updateDocument. */
    private static boolean isBanned(Document doc) {
        return DocumentStatus.BANNED.name().equalsIgnoreCase(doc.getStatus());
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

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId) {
        // Verify folder exists and user has access — single check instead of per-document
        var folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (!isAdmin() && !folder.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to access this folder");

        return documentRepository
                .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId)
                .stream()
                // Business: folder KHÔNG hiển thị document BANNED (chúng ẩn khỏi folder;
                // owner vẫn thấy chúng ở My Documents đầy đủ qua getDocumentsByOwner).
                .filter(d -> !isBanned(d))
                .map(documentMapper::toResponse)
                .map(this::sanitizeForListing)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(UUID id, UUID ownerId) {
        Document document = getAccessibleDocument(id, ownerId, false);
        DocumentResponse resp = documentMapper.toResponse(document);
        // BANNED — không trả cloudinaryUrl cho ai ngoài admin (owner chỉ thấy metadata).
        if (isBanned(document) && !isAdmin())
            resp.setCloudinaryUrl(null);
        // Strip file URL cho non-READY — chỉ strip cho non-owner và non-admin.
        else if (!DocumentStatus.READY.name().equalsIgnoreCase(resp.getStatus()) && !isAdmin() && !document.getOwnerId().equals(ownerId))
            resp.setCloudinaryUrl(null);
        return resp;
    }

    @Override
    public DocumentResponse updateDocument(UUID id, UUID ownerId, DocumentUpdateRequest request) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!isAdmin() && !document.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to update this document");

        // Business rule: BANNED — owner không được edit (chỉ xóa permanent + appeal tương lai).
        if (isBanned(document))
            throw new AccessDeniedException("Tài liệu đã bị cấm. Không thể chỉnh sửa.");

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

        if (!document.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to delete this document");

        document.setDeletedAt(LocalDateTime.now());
        document.setDeletedByFolder(false);
        documentRepository.save(document);
    }

    @Override
    @Transactional
    public void permanentDeleteDocument(UUID id, UUID requesterId) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        // 1) Must already be in trash (soft-deleted first)
        if (document.getDeletedAt() == null)
            throw new IllegalArgumentException("Document must be in trash before permanent deletion");

        boolean isOwner = document.getOwnerId().equals(requesterId);
        if (!isOwner && !isAdmin())
            throw new AccessDeniedException("You do not have permission to permanently delete this document");

        // 2) Remove chat history
        chatSessionRepository.deleteByDocumentId(id);

        // 3) Keep publicId before deleting entity
        String publicId = document.getPublicId();

        // 3b) PERMANENT delete — giảm used_storage_bytes (không bao giờ âm).
        //     Soft delete KHÔNG giảm, restore KHÔNG tăng. Chỉ event này được trừ.
        if (document.getFileSize() != null) {
            storageQuotaService.subtractUsedBytes(document.getOwnerId(), document.getFileSize());
        }

        // 4) Delete document (child tables are removed by ON DELETE CASCADE)
        documentRepository.delete(document);
        documentRepository.flush();

        // No Cloudinary file
        if (publicId == null || publicId.isBlank()) return;

        // Still referenced by other documents -> don't delete Cloudinary
        if (documentRepository.countByPublicId(publicId) > 0) return;

        // 5) Last reference -> delete Cloudinary file
        try {
            cloudinaryService.delete(publicId);
        } catch (Exception e) {
            throw e;
        }
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

        if (document.getDeletedAt() == null)
            throw new RuntimeException("Document is not in trash");

        if (Boolean.TRUE.equals(document.getDeletedByFolder()))
            throw new RuntimeException("Document was deleted via folder deletion. Restore the parent folder first.");

        // Check parent folder is not deleted
        if (document.getFolderId() != null)
            folderRepository.findByIdAndDeletedAtIsNull(document.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Parent folder is deleted. Restore the parent folder first."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId))
            throw new AccessDeniedException("You do not have permission to restore this document");

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
    public Document getAccessibleDocument(UUID documentId, UUID requesterId, boolean aiRequired) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Admin always bypasses status checks
        if (isAdmin()) return document;

        // Owner: view metadata (aiRequired=false) cho phép mọi status, kể cả BANNED —
        // owner cần thấy trạng thái để xóa/appeal. Viewer content vẫn bị chặn
        // (cloudinaryUrl strip ở sanitizeForListing).
        if (document.getOwnerId().equals(requesterId) && !aiRequired) return document;

        // BANNED — chặn mọi action: download, AI, share, viewer nội dung.
        if (DocumentStatus.BANNED.name().equalsIgnoreCase(document.getStatus()))
            throw new AccessDeniedException("Tài liệu đã bị cấm.");

        // Owner: AI operations require READY status
        if (document.getOwnerId().equals(requesterId)) {
            if (!DocumentStatus.READY.name().equalsIgnoreCase(document.getStatus()))
                throw new AccessDeniedException("Tài liệu chưa được phê duyệt. Vui lòng đợi quản trị viên xét duyệt.");
            return document;
        }

        // Shared user: must have share access AND document must be READY
        if (hasShareAccess(documentId, requesterId)) {
            if (!DocumentStatus.READY.name().equalsIgnoreCase(document.getStatus()))
                throw new AccessDeniedException("Tài liệu chưa được phê duyệt.");
            return document;
        }
        throw new AccessDeniedException("You do not have permission to access this document");
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
    @Transactional(readOnly = true)
    public List<DocumentResponse> getSharedFolderDocuments(UUID userId, UUID folderId) {
        boolean hasFolderShareAccess = shareRepository.existsByFolderIdAndSharedAccountIdAndRevokedFalse(folderId, userId);
        if (!hasFolderShareAccess && !isAdmin())
            throw new AccessDeniedException("You do not have permission to access this shared folder");

        List<Document> documents = documentRepository
                .findByFolderIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(folderId, DocumentStatus.READY.name())
                .stream()
                .filter(d -> !isBanned(d))
                .toList();

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    // ============ HELPER METHODS ============

    /** Strip file URL từ listing — non-READY (COMPLETED/REJECT/REPORTED/BANNED) không lộ content.
     *  Đặc biệt BANNED: owner chỉ thấy metadata, admin vẫn có URL (qua endpoint riêng). */
    private DocumentResponse sanitizeForListing(DocumentResponse resp) {
        if (!DocumentStatus.READY.name().equalsIgnoreCase(resp.getStatus()))
            resp.setCloudinaryUrl(null);
        return resp;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}