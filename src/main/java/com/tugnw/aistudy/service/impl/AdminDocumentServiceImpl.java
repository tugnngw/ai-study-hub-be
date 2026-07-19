package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ActivityLogService;
import com.tugnw.aistudy.service.AdminDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminDocumentServiceImpl implements AdminDocumentService {

    private final DocumentRepository documentRepository;
    private final ActivityLogService activityLogService;
    private final ShareRepository shareRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAllByDeletedAtIsNull().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByStatus(String status) {
        String targetStatus = switch (status.toUpperCase()) {
            case "PENDING"   -> "COMPLETED";
            case "APPROVED"  -> "READY";
            case "REJECTED"  -> "REJECT";
            default          -> status.toUpperCase();
        };
        return documentRepository.findByStatusAndDeletedAtIsNull(targetStatus).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getTrashDocuments() {
        return documentRepository.findByDeletedAtIsNotNullOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void deleteDocument(UUID id) {
        throw new RuntimeException("Admin cannot delete user documents. Only document owners can delete their own documents.");
    }

    @Override
    public void restoreDocument(UUID id, UUID adminId, String adminName) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getDeletedAt() == null) {
            throw new RuntimeException("Document is not in trash");
        }

        document.setDeletedAt(null);
        documentRepository.save(document);

        String description = String.format("Admin '%s' restored document '%s' (ID: %s) for user %s",
                adminName, document.getTitle(), id, document.getOwnerId());
        activityLogService.logActivity(adminId, adminName, ActivityType.DOCUMENT_RESTORE, description);

        log.info("[ADMIN RESTORE] Admin {} restored document {} for user {}", adminId, id, document.getOwnerId());
    }

    @Override
    @Transactional
    public void approveDocument(UUID id) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        log.info("[ADMIN] Approving document: {} status: {}", id, document.getStatus());
        document.setStatus("READY");
        documentRepository.saveAndFlush(document);
        log.info("[ADMIN] Document {} approved, status: {}", id, document.getStatus());
    }

    @Override
    @Transactional
    public void rejectDocument(UUID id, String reason) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setStatus("REJECT");
        document.setRejectReason(reason);
        documentRepository.save(document);

        shareRepository.findByDocumentId(id).forEach(share -> {
            log.info("[ADMIN REJECT] Revoking share {} for rejected document {}", share.getId(), id);
            shareRepository.delete(share);
        });

        log.info("[ADMIN REJECT] Document {} rejected with reason: {} - all shares revoked", id, reason);
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setOwnerId(document.getOwnerId());
        response.setFolderId(document.getFolderId());
        response.setSubjectId(document.getSubjectId());
        response.setTitle(document.getTitle());
        response.setDescription(document.getDescription());
        response.setSummary(document.getSummary());
        response.setStatus(document.getStatus());
        response.setMimeType(document.getMimeType());
        response.setFileSize(document.getFileSize());
        response.setCloudinaryUrl(document.getCloudinaryUrl());
        response.setCreatedAt(document.getCreatedAt());
        response.setDeletedAt(document.getDeletedAt());
        response.setRejectReason(document.getRejectReason());
        response.setAiStatus(document.getAiStatus() != null ? document.getAiStatus().name() : null);
        return response;
    }
}
