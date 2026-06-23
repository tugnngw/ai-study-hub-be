package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.Share;
import com.tugnw.aistudy.repository.AccountRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.ShareRepository;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
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

    @Override
    public ShareResponse shareFolder(ShareRequest request, UUID ownerId) {
        // 1. Find user to share with - try email first, then username
        Account targetUser = null;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            targetUser = accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail()).orElse(null);
        }
        if (targetUser == null && request.getUsername() != null && !request.getUsername().isBlank()) {
            targetUser = accountRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(request.getUsername());
        }
        
        if (targetUser == null) {
            String searchBy = request.getEmail() != null ? request.getEmail() : request.getUsername();
            throw new IllegalArgumentException("User not found: " + searchBy);
        }
        if (targetUser.getId().equals(ownerId)) {
            throw new IllegalArgumentException("Cannot share to yourself");
        }

        // 2. Find folder and check ownership
        Folder folder = folderRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getFolderId(), ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found or you don't have permission"));

        // 3. Check if already shared
        if (shareRepository.findByFolderIdAndSharedAccountId(folder.getId(), targetUser.getId()).isPresent()) {
            throw new IllegalArgumentException("Already shared with this user");
        }

        // 4. Create share
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
    public List<ShareResponse> getSharesByOwner(UUID ownerId) {
        return shareRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
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
        if (!share.getOwner().getId().equals(ownerId)) {
            throw new IllegalArgumentException("You don't have permission to remove this share");
        }
        shareRepository.delete(share);
    }

    @Override
    public String getShareLink(UUID folderId) {
        return "http://localhost:5174/shared/" + folderId;
    }

    @Override
    public Share getShareEntity(Long shareId) {
        return shareRepository.findById(shareId).orElseThrow(() -> new IllegalArgumentException("Share not found"));
    }

    private ShareResponse mapToResponse(Share share) {
        return new ShareResponse(
                share.getId(),
                share.getFolder().getId(),
                share.getOwner().getId(),
                share.getSharedAccount() != null ? share.getSharedAccount().getId() : null,
                share.getSharedAccount() != null ? share.getSharedAccount().getUsername() : null,
                share.getSharedAccount() != null ? share.getSharedAccount().getEmail() : null,
                share.getVisibility(),
                share.getCreatedAt()
        );
    }

}
