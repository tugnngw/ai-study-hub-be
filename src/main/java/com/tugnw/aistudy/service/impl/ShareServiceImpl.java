package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.Share;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShareServiceImpl implements ShareService {
    private final ShareRepository shareRepository;
    private final AccountRepository accountRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Account findTargetUser(ShareRequest request) {
        Account targetUser = null;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            targetUser = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail()).orElse(null);
        }
        if (targetUser == null && request.getUsername() != null && !request.getUsername().isBlank()) {
            targetUser = accountRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(request.getUsername());
        }
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
        if ((request.getEmail() == null || request.getEmail().isBlank()) &&
                (request.getUsername() == null || request.getUsername().isBlank())) {
            Document document = documentRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getDocumentId(), ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found or you don't have permission"));
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
        
        Document document = documentRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getDocumentId(), ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found or you don't have permission"));
        
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
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void removeShare(Long shareId, UUID ownerId) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!isAdmin() && !share.getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("You don't have permission to remove this share");
        }
        shareRepository.delete(share);
    }

    @Override
    public ShareResponse saveToMyFolder(Long shareId, UUID folderId, String title, String description) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        UUID newOwnerId = share.getOwner().getId();

        if (share.getDocument() != null) {
            Document original = share.getDocument();
            Document copy = copyDocument(original, folderId, newOwnerId, title, description);
            Document savedDoc = documentRepository.save(copy);

            return new ShareResponse(
                    share.getId(),
                    folderId,
                    savedDoc.getId(),
                    savedDoc.getOwnerId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    "private",
                    null,
                    null,
                    null,
                    List.of(),
                    savedDoc.getTitle(),
                    null
            );
        }

        if (share.getFolder() == null) {
            throw new IllegalArgumentException("Shared item not found");
        }

        List<Document> documents = documentRepository.findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(share.getFolder().getId());
        for (Document document : documents) {
            documentRepository.save(copyDocument(document, folderId, newOwnerId, null, null));
        }

        return new ShareResponse(
                share.getId(),
                folderId,
                null,
                newOwnerId,
                null,
                null,
                null,
                null,
                null,
                "private",
                null,
                null,
                null,
                List.of(),
                null,
                share.getFolder().getName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String getShareLink(UUID folderId) {
        return frontendUrl + "/shared/" + folderId;
    }

    @Override
    @Transactional(readOnly = true)
    public Share getShareEntity(Long shareId) {
        return shareRepository.findById(shareId).orElseThrow(() -> new IllegalArgumentException("Share not found"));
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
                folderName
        );
    }

    private ShareResponse mapToResponse(Share share) {
        String shareLink = frontendUrl + "/shared/" + share.getShareToken();
        String documentTitle = share.getDocument() != null ? share.getDocument().getTitle() : null;
        String folderName = share.getFolder() != null ? share.getFolder().getName() : null;
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
                folderName
        );
    }

    private Document copyDocument(Document original, UUID folderId, UUID ownerId, String title, String description) {
        return Document.builder()
                .ownerId(ownerId)
                .folderId(folderId)
                .title(title != null && !title.isBlank() ? title : original.getTitle())
                .description(description != null ? description : original.getDescription())
                .summary(original.getSummary())
                .status("ready")
                .cloudinaryUrl(original.getCloudinaryUrl())
                .publicId(original.getPublicId())
                .mimeType(original.getMimeType())
                .checksum(original.getChecksum())
                .fileSize(original.getFileSize())
                .totalPages(original.getTotalPages())
                .build();
    }
}
