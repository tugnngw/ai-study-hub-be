package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.Subject;
import com.tugnw.aistudy.domain.mapper.FolderMapper;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.SubjectRepository;
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
    private final SubjectRepository subjectRepository;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public FolderResponse createFolder(UUID ownerId, FolderCreateRequest request) {
        boolean exists = folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName());
        if (exists) {
            throw new RuntimeException("Folder with this name already exists");
        }

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        Folder folder = folderMapper.toEntity(request);
        folder.setOwnerId(ownerId);
        folder.setSubject(subject);

        Folder savedFolder = folderRepository.save(folder);
        return folderMapper.toResponse(savedFolder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getFoldersByOwner(UUID ownerId) {
        List<Folder> folders = folderRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);

        return folders.stream()
                .map(folderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getFolderById(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to access this folder");
        }

        return folderMapper.toResponse(folder);
    }

    @Override
    public FolderResponse updateFolder(UUID id, UUID ownerId, FolderUpdateRequest request) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to update this folder");
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()
                && !request.getName().equals(folder.getName())) {

            boolean exists = folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName());
            if (exists) {
                throw new RuntimeException("Folder with this name already exists");
            }
            folder.setName(request.getName());
        }

        if (request.getSubjectId() != null) {
            Subject subject = subjectRepository.findById(request.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Subject not found"));
            folder.setSubject(subject);
        }

        Folder updatedFolder = folderRepository.save(folder);
        return folderMapper.toResponse(updatedFolder);
    }

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
