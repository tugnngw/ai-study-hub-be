package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface FolderService {

    FolderResponse createFolder(UUID ownerId, FolderCreateRequest request);

    List<FolderResponse> getFoldersByOwner(UUID ownerId);

    FolderResponse getFolderById(UUID id, UUID ownerId);

    FolderResponse updateFolder(UUID id, UUID ownerId, FolderUpdateRequest request);

    void deleteFolder(UUID id, UUID ownerId);
    List<FolderResponse> getTrashFolders(UUID requesterId);
    void restoreFolder(UUID id, UUID requesterId);
    void permanentDeleteFolder(UUID id, UUID requesterId);
}
