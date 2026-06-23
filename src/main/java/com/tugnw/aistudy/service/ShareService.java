package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.domain.entity.Share;
import java.util.List;
import java.util.UUID;

public interface ShareService {
    ShareResponse shareFolder(ShareRequest request, UUID ownerId);
    List<ShareResponse> getSharesByOwner(UUID ownerId);
    List<ShareResponse> getSharesWithMe(UUID userId);
    void removeShare(Long shareId, UUID ownerId);
    String getShareLink(UUID folderId);
    Share getShareEntity(Long shareId);
}
