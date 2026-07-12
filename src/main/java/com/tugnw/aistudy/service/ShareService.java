package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.share.SaveToFolderResponse;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.domain.entity.Share;
import java.util.List;
import java.util.UUID;

public interface ShareService {
    ShareResponse shareFolder(ShareRequest request, UUID ownerId);
    ShareResponse shareDocument(ShareRequest request, UUID ownerId);
    List<ShareResponse> getSharesByOwner(UUID ownerId);
    List<ShareResponse> getSharesWithMe(UUID userId);
    void removeShare(UUID shareId, UUID ownerId);
    void removeShareByToken(String shareToken, UUID ownerId);
    SaveToFolderResponse saveToMyFolder(UUID shareId, UUID folderId, String title, String description, UUID requesterId);
    String getShareLink(UUID folderId);
    Share getShareEntity(UUID shareId);
    ShareResponse getShareInfo(UUID id, String type, UUID ownerId);
}
