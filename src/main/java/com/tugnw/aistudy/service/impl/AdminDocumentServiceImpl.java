package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.ActivityType;
import com.tugnw.aistudy.domain.enums.DocumentStatus;
import com.tugnw.aistudy.domain.mapper.DocumentMapper;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
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
    private final FolderRepository folderRepository;
    private final ActivityLogService activityLogService;
    private final ShareRepository shareRepository;
    private final DocumentMapper documentMapper;
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAllByDeletedAtIsNull().stream()
                .map(doc -> {
                    DocumentResponse resp = documentMapper.toResponse(doc);
                    accountRepository.findById(doc.getOwnerId()).ifPresent(acc -> resp.setOwnerName(acc.getUsername()));
                    return resp;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByStatus(String status) {
        return documentRepository.findByStatusAndDeletedAtIsNull(status).stream()
                .map(doc -> {
                    DocumentResponse resp = documentMapper.toResponse(doc);
                    accountRepository.findById(doc.getOwnerId()).ifPresent(acc -> resp.setOwnerName(acc.getUsername()));
                    return resp;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getTrashDocuments() {
        return documentRepository.findByDeletedAtIsNotNullOrderByCreatedAtDesc().stream()
                .map(doc -> {
                    DocumentResponse resp = documentMapper.toResponse(doc);
                    accountRepository.findById(doc.getOwnerId()).ifPresent(acc -> resp.setOwnerName(acc.getUsername()));
                    return resp;
                })
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

        if (document.getDeletedAt() == null)
            throw new RuntimeException("Document is not in trash");

        if (Boolean.TRUE.equals(document.getDeletedByFolder()))
            throw new RuntimeException("Document was deleted via folder deletion. Restore the parent folder first.");

        // Check parent folder is not deleted
        if (document.getFolderId() != null) {
            folderRepository.findByIdAndDeletedAtIsNull(document.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Parent folder is deleted. Restore the parent folder first."));
        }

        document.setDeletedAt(null);
        documentRepository.save(document);

        String description = String.format("Admin '%s' restored document '%s' (ID: %s) for user %s",
                adminName, document.getTitle(), id, document.getOwnerId());
        activityLogService.logActivity(adminId, adminName, ActivityType.DOCUMENT_RESTORE, description);
    }

    @Override
    @Transactional
    public void approveDocument(UUID id) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        document.setStatus(DocumentStatus.READY.name());
        documentRepository.saveAndFlush(document);
    }

    @Override
    @Transactional
    public void rejectDocument(UUID id, String reason) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setStatus(DocumentStatus.REJECT.name());
        document.setRejectReason(reason);
        documentRepository.save(document);

        shareRepository.findByDocumentId(id).forEach(share -> {
            shareRepository.deleteByDocumentId(share.getDocument().getId());
        });
    }

}
