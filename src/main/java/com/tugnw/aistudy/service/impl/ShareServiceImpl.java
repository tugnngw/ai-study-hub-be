package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.share.SaveToFolderResponse;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.Share;

import com.tugnw.aistudy.domain.enums.DocumentStatus;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShareServiceImpl implements ShareService {
    private final ShareRepository shareRepository;
    private final AccountRepository accountRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Account findTargetUser(ShareRequest request) {
        Account targetUser = null;
        if (request.getEmail() != null && !request.getEmail().isBlank())
            targetUser = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail()).orElse(null);
        if (targetUser == null && request.getUsername() != null && !request.getUsername().isBlank())
            targetUser = accountRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(request.getUsername());
        return targetUser;
    }

    @Override
    public ShareResponse shareFolder(ShareRequest request, UUID ownerId) {
        if ((request.getEmail() == null || request.getEmail().isBlank()) &&
                (request.getUsername() == null || request.getUsername().isBlank())) {
            Folder folder = folderRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getFolderId(), ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found or you don't have permission"));
            Account owner = accountRepository.findById(ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
            Share share = Share.builder()
                    .folder(folder)
                    .owner(owner)
                    .visibility(request.getVisibility())
                    .build();
            Share saved = shareRepository.save(share);
            return mapToResponse(saved);
        }

        Account targetUser = findTargetUser(request);
        if (targetUser == null) {
            String searchBy = request.getEmail() != null ? request.getEmail() : request.getUsername();
            throw new IllegalArgumentException("User not found: " + searchBy);
        }
        if (targetUser.getId().equals(ownerId)) {
            throw new IllegalArgumentException("Cannot share to yourself");
        }

        Folder folder = folderRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getFolderId(), ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or you don't have permission"));

        if (shareRepository.findByFolderIdAndSharedAccountId(folder.getId(), targetUser.getId()).isPresent()) {
            throw new IllegalArgumentException("Already shared with this user");
        }

        Account owner = accountRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        Share share = Share.builder()
                .folder(folder)
                .owner(owner)
                .sharedAccount(targetUser)
                .visibility(request.getVisibility())
                .build();
        Share saved = shareRepository.save(share);
        return mapToResponse(saved);
    }

    @Override
    public ShareResponse shareDocument(ShareRequest request, UUID ownerId) {
        // Only READY documents can be shared
        Document document = documentService.getAccessibleDocument(request.getDocumentId(), ownerId, true);

        if ((request.getEmail() == null || request.getEmail().isBlank()) &&
                (request.getUsername() == null || request.getUsername().isBlank())) {
            Account owner = accountRepository.findById(ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
            Share share = Share.builder()
                    .document(document)
                    .owner(owner)
                    .visibility(request.getVisibility())
                    .build();
            Share saved = shareRepository.save(share);
            return mapToResponse(saved);
        }

        Account targetUser = findTargetUser(request);
        if (targetUser == null) {
            String searchBy = request.getEmail() != null ? request.getEmail() : request.getUsername();
            throw new IllegalArgumentException("User not found: " + searchBy);
        }
        if (targetUser.getId().equals(ownerId)) {
            throw new IllegalArgumentException("Cannot share to yourself");
        }

        if (shareRepository.findByDocumentIdAndSharedAccountId(document.getId(), targetUser.getId()).isPresent()) {
            throw new IllegalArgumentException("Already shared with this user");
        }

        Account owner = accountRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        Share share = Share.builder()
                .document(document)
                .owner(owner)
                .sharedAccount(targetUser)
                .visibility(request.getVisibility())
                .build();
        Share saved = shareRepository.save(share);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareResponse> getSharesByOwner(UUID ownerId) {
        return shareRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareResponse> getSharesWithMe(UUID userId) {
        return shareRepository.findBySharedAccountId(userId)
                .stream()
                .filter(share -> {
                    if (share.getDocument() != null) {
                        String status = share.getDocument().getStatus();
                        return DocumentStatus.READY.name().equalsIgnoreCase(status);
                    }
                    if (share.getFolder() != null) {
                        return true;
                    }
                    return false;
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void removeShare(UUID shareId, UUID ownerId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!isAdmin() && !share.getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("You don't have permission to remove this share");
        }
        shareRepository.delete(share);
    }

    @Override
    public void removeShareByToken(String shareToken, UUID userId) {
        Share share = shareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!isAdmin() && !share.getOwner().getId().equals(userId) &&
            !(share.getSharedAccount() != null && share.getSharedAccount().getId().equals(userId))) {
            throw new IllegalArgumentException("You don't have permission to remove this share");
        }
        shareRepository.delete(share);
    }

    @Override
    public SaveToFolderResponse saveToMyFolder(UUID shareId, UUID folderId, String title, String description, UUID requesterId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        // SECURITY: share đã revoke → không save
        if (share.getRevoked())
            throw new IllegalArgumentException("This share has been revoked");
        // SECURITY: share hết hạn → không save
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("This share has expired");
        // SECURITY: PRIVATE → chỉ sharedAccount (hoặc owner) được save; PUBLIC → cho phép
        boolean isPublic = "public".equalsIgnoreCase(share.getVisibility());
        boolean isRecipient = share.getSharedAccount() != null
                && share.getSharedAccount().getId().equals(requesterId);
        boolean isOwner = share.getOwner() != null && share.getOwner().getId().equals(requesterId);
        if (!isPublic && !isRecipient && !isOwner)
            throw new IllegalArgumentException("You don't have access to this share");
        // SECURITY: folder đích phải thuộc requester
        if (folderId == null
                || folderRepository.findByIdAndOwnerIdAndDeletedAtIsNull(folderId, requesterId).isEmpty())
            throw new IllegalArgumentException("Destination folder not found or not owned by you");

        List<Document> sourceDocs;

        if (share.getDocument() != null) {
            sourceDocs = List.of(share.getDocument());
        } else if (share.getFolder() != null) {
            sourceDocs = documentRepository
                    .findByFolderIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(share.getFolder().getId(), DocumentStatus.READY.name());
        } else {
            throw new IllegalArgumentException("Shared item not found");
        }

        List<Document> existingDocs = documentRepository
                .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);

        List<SaveToFolderResponse.DocumentResult> copied = new ArrayList<>();
        List<SaveToFolderResponse.DocumentResult> skipped = new ArrayList<>();
        List<SaveToFolderResponse.DocumentResult> failed = new ArrayList<>();

        for (Document source : sourceDocs) {
            String effectiveTitle = source.getTitle();
            if (share.getDocument() != null && title != null && !title.isBlank()) {
                effectiveTitle = title;
            }

            String reason = isDuplicate(source, existingDocs);
            if (reason != null) {
                skipped.add(new SaveToFolderResponse.DocumentResult(effectiveTitle, null, reason));
                continue;
            }

            try {
                Document copy = buildCopy(source, folderId, requesterId, effectiveTitle,
                        share.getDocument() != null ? description : null);
                Document saved = documentRepository.save(copy);
                copied.add(new SaveToFolderResponse.DocumentResult(effectiveTitle, saved.getId(), null));
            } catch (Exception e) {
                failed.add(new SaveToFolderResponse.DocumentResult(effectiveTitle, null, e.getMessage()));
            }
        }

        return buildResponse(sourceDocs.size(), copied, skipped, failed);
    }

    private String isDuplicate(Document source, List<Document> existingDocs) {
        for (Document existing : existingDocs) {
            if (source.getChecksum() != null && !source.getChecksum().isBlank()
                    && source.getChecksum().equals(existing.getChecksum())) {
                return "Already exists";
            }
            if (source.getPublicId() != null && !source.getPublicId().isBlank()
                    && source.getPublicId().equals(existing.getPublicId())) {
                return "Already exists";
            }
            if (source.getTitle() != null && existing.getTitle() != null
                    && source.getTitle().equals(existing.getTitle())
                    && source.getFileSize() != null && existing.getFileSize() != null
                    && source.getFileSize().equals(existing.getFileSize())) {
                return "Already exists";
            }
        }
        return null;
    }

    private Document buildCopy(Document source, UUID folderId, UUID ownerId, String title, String description) {
        return Document.builder()
                .ownerId(ownerId)
                .folderId(folderId)
                .title(title)
                .description(description != null ? description : source.getDescription())
                .summary(source.getSummary())
                .status(DocumentStatus.READY.name())
                .cloudinaryUrl(source.getCloudinaryUrl())
                .publicId(source.getPublicId())
                .mimeType(source.getMimeType())
                .checksum(source.getChecksum())
                .fileSize(source.getFileSize())
                .totalPages(source.getTotalPages())
                .build();
    }

    private SaveToFolderResponse buildResponse(int total,
                                               List<SaveToFolderResponse.DocumentResult> copied,
                                               List<SaveToFolderResponse.DocumentResult> skipped,
                                               List<SaveToFolderResponse.DocumentResult> failed) {
        StringBuilder msg = new StringBuilder();
        if (copied.size() > 0) {
            msg.append(copied.size()).append(" document").append(copied.size() != 1 ? "s" : "")
               .append(" copied successfully");
        }
        if (skipped.size() > 0) {
            if (msg.length() > 0) msg.append(". ");
            msg.append(skipped.size()).append(" document").append(skipped.size() != 1 ? "s were" : " was")
               .append(" skipped because they already exist");
        }
        if (failed.size() > 0) {
            if (msg.length() > 0) msg.append(". ");
            msg.append(failed.size()).append(" document").append(failed.size() != 1 ? "s" : "")
               .append(" failed to copy");
        }
        if (msg.isEmpty()) {
            msg.append("No documents to copy");
        }

        return SaveToFolderResponse.builder()
                .total(total)
                .copied(copied.size())
                .skipped(skipped.size())
                .failed(failed.size())
                .copiedDocuments(copied)
                .skippedDocuments(skipped)
                .failedDocuments(failed)
                .message(msg.toString())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String getShareLink(UUID folderId) {
        return frontendUrl + "/shared/" + folderId;
    }

    @Override
    @Transactional(readOnly = true)
    public ShareResponse getShareInfo(UUID id, String type, UUID ownerId) {
        List<Share> shares = type.equals("document")
                ? shareRepository.findByDocumentId(id)
                : shareRepository.findByFolderId(id);

        List<ShareResponse.ShareRecipient> recipients = shares.stream()
                .filter(s -> isAdmin() || s.getOwner().getId().equals(ownerId))
                .filter(s -> s.getSharedAccount() != null && !s.getRevoked())
                .map(s -> new ShareResponse.ShareRecipient(
                        s.getSharedAccount().getId(),
                        s.getSharedAccount().getEmail(),
                        s.getSharedAccount().getUsername(),
                        s.getSharedAccount().getFullName()
                ))
                .collect(Collectors.toList());

        String shareToken = shares.isEmpty() ? null : shares.get(0).getShareToken();
        String shareLink = shareToken != null ? frontendUrl + "/shared/" + shareToken : "";

        String documentTitle = null;
        String folderName = null;
        if (!shares.isEmpty()) {
            Share share = shares.get(0);
            if (share.getDocument() != null) {
                documentTitle = share.getDocument().getTitle();
            } else if (share.getFolder() != null) {
                folderName = share.getFolder().getName();
            }
        }

        String ownerUsername = shares.isEmpty() ? null : shares.get(0).getOwner().getUsername();
        String ownerEmail = shares.isEmpty() ? null : shares.get(0).getOwner().getEmail();
        String cloudinaryUrl = null;
        if (!shares.isEmpty() && shares.get(0).getDocument() != null) {
            cloudinaryUrl = shares.get(0).getDocument().getCloudinaryUrl();
        }

        return new ShareResponse(
                shares.isEmpty() ? null : shares.get(0).getId(),
                type.equals("folder") ? id : null,
                type.equals("document") ? id : null,
                ownerId,
                ownerUsername,
                ownerEmail,
                null,
                null,
                null,
                "private",
                shareToken,
                shareLink,
                null,
                recipients,
                documentTitle,
                folderName,
                cloudinaryUrl,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ShareResponse getShareByToken(String shareToken) {
        Share share = shareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (share.getRevoked())
            throw new IllegalArgumentException("This share has been revoked");
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("This share has expired");
        // Only READY documents are accessible via public share
        if (share.getDocument() != null && !DocumentStatus.READY.name().equalsIgnoreCase(share.getDocument().getStatus()))
            throw new IllegalArgumentException("Tài liệu chưa được phê duyệt.");
        return mapToResponse(share);
    }

    @Override
    @Transactional(readOnly = true)
    public String getShareLinkByToken(String shareToken) {
        Share share = shareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        return frontendUrl + "/shared/" + share.getShareToken();
    }

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrlByToken(String shareToken) {
        Share share = shareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (share.getRevoked())
            throw new IllegalArgumentException("This share has been revoked");
        if (share.getDocument() != null) {
            if (!DocumentStatus.READY.name().equalsIgnoreCase(share.getDocument().getStatus()))
                throw new IllegalArgumentException("Tài liệu chưa được phê duyệt.");
            return share.getDocument().getCloudinaryUrl();
        }
        throw new IllegalArgumentException("No downloadable file for this share");
    }

    private ShareResponse mapToResponse(Share share) {
        String shareLink = frontendUrl + "/shared/" + share.getShareToken();
        String documentTitle = share.getDocument() != null ? share.getDocument().getTitle() : null;
        String folderName = null;
        if (share.getFolder() != null) {
            folderName = share.getFolder().getName();
        } else if (share.getDocument() != null && share.getDocument().getFolderId() != null) {
            // Doc share: hiển thị tên folder chứa document
            Folder docFolder = folderRepository.findById(share.getDocument().getFolderId()).orElse(null);
            folderName = docFolder != null ? docFolder.getName() : null;
        }
        String cloudinaryUrl = share.getDocument() != null ? share.getDocument().getCloudinaryUrl() : null;
        String documentStatus = share.getDocument() != null ? share.getDocument().getStatus() : null;

        // Metadata hiển thị: size + semester/subject (từ entity — không thêm query)
        Long documentFileSize = share.getDocument() != null
                ? share.getDocument().getFileSize() : null;
        Long folderSizeBytes = null;
        if (share.getFolder() != null) {
            List<Object[]> agg = documentRepository.countAndSizeByFolderId(share.getFolder().getId());
            if (!agg.isEmpty() && agg.get(0)[1] != null)
                folderSizeBytes = (Long) agg.get(0)[1];
        }
        String subjectName = null;
        String semesterName = null;
        if (share.getFolder() != null && share.getFolder().getSubject() != null) {
            subjectName = share.getFolder().getSubject().getName();
            if (share.getFolder().getSubject().getSemester() != null)
                semesterName = share.getFolder().getSubject().getSemester().getName();
        } else if (share.getDocument() != null && share.getDocument().getFolderId() != null) {
            // Fallback: tìm folder của document nếu có
            Folder folder = share.getDocument().getFolderId() != null
                    ? folderRepository.findById(share.getDocument().getFolderId()).orElse(null)
                    : null;
            if (folder != null && folder.getSubject() != null) {
                subjectName = folder.getSubject().getName();
                if (folder.getSubject().getSemester() != null)
                    semesterName = folder.getSubject().getSemester().getName();
            }
        }
        return new ShareResponse(
                share.getId(),
                share.getFolder() != null ? share.getFolder().getId() : null,
                share.getDocument() != null ? share.getDocument().getId() : null,
                share.getOwner().getId(),
                share.getOwner().getUsername(),
                share.getOwner().getEmail(),
                share.getSharedAccount() != null ? share.getSharedAccount().getId() : null,
                share.getSharedAccount() != null ? share.getSharedAccount().getUsername() : null,
                share.getSharedAccount() != null ? share.getSharedAccount().getEmail() : null,
                share.getVisibility(),
                share.getShareToken(),
                shareLink,
                share.getCreatedAt(),
                List.of(),
                documentTitle,
                folderName,
                cloudinaryUrl,
                documentStatus,
                documentFileSize,
                folderSizeBytes,
                subjectName,
                semesterName
        );
    }
}
