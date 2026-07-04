package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.mapper.FolderMapper;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final FolderMapper folderMapper;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }


    //Create a new folder
    @Override
    public FolderResponse createFolder(UUID ownerId, FolderCreateRequest request) {
        // Check if folder name already exists for this user
        boolean exists = folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName());
        if (exists) {
            throw new RuntimeException("Folder with this name already exists");
        }

        Folder folder = folderMapper.toEntity(request);
        folder.setOwnerId(ownerId);

        Folder savedFolder = folderRepository.save(folder);
        return folderMapper.toResponse(savedFolder);
    }


    //Get all folders of a user
    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getFoldersByOwner(UUID ownerId) {
        List<Folder> folders = folderRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);

        return folders.stream()
                .map(folderMapper::toResponse)
                .toList();
    }

    // Get folder by ID
    @Override
    @Transactional(readOnly = true)
    public FolderResponse getFolderById(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        // Check ownership
        if (!isAdmin() && !folder.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to access this folder");
        }

        return folderMapper.toResponse(folder);
    }


    //Update folder
    @Override
    public FolderResponse updateFolder(UUID id, UUID ownerId, FolderUpdateRequest request) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        // Check ownership
        if (!isAdmin() && !folder.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to update this folder");
        }

        // Check duplicate name if name is changed
        if (request.getName() != null && !request.getName().trim().isEmpty()
                && !request.getName().equals(folder.getName())) {

            boolean exists = folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName());
            if (exists) {
                throw new RuntimeException("Folder with this name already exists");
            }
            folder.setName(request.getName());
        }

        Folder updatedFolder = folderRepository.save(folder);
        return folderMapper.toResponse(updatedFolder);
    }

    //Delete folder (Soft delete)
    @Override
    public void deleteFolder(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to delete this folder");
        }

        folder.setDeletedAt(LocalDateTime.now());
        folderRepository.save(folder);
    }
}