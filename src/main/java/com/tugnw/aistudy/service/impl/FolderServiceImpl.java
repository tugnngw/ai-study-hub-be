package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.domain.entity.Subject;
import com.tugnw.aistudy.domain.mapper.FolderMapper;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.repository.SubjectRepository;
import com.tugnw.aistudy.service.CloudinaryService;
import com.tugnw.aistudy.service.FolderService;
import com.tugnw.aistudy.service.StorageQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final FolderMapper folderMapper;
    private final SubjectRepository subjectRepository;
    private final DocumentRepository documentRepository;
    private final StorageQuotaService storageQuotaService;
    private final ChatSessionRepository chatSessionRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    public FolderResponse createFolder(UUID ownerId, FolderCreateRequest request) {
        boolean exists = folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName());
        if (exists)
            throw new RuntimeException("Folder with this name already exists");

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        Folder folder = folderMapper.toEntity(request);
        folder.setOwnerId(ownerId);
        folder.setSubject(subject);

        Folder savedFolder = folderRepository.save(folder);
        FolderResponse resp = folderMapper.toResponse(savedFolder);
        resp.setDocumentCount(0);
        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getFoldersByOwner(UUID ownerId) {
        List<Folder> folders = folderRepository.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);

        // Group-by count + size 1 query thay N query riêng (chống N+1).
        // Chỉ đếm document hiển thị (đã exclude BANNED trong query).
        List<UUID> folderIds = folders.stream().map(Folder::getId).toList();
        Map<UUID, long[]> agg = folderIds.isEmpty()
                ? Map.of()
                : documentRepository.countAndSizeByFolderIdsGroupBy(folderIds).stream()
                        .collect(Collectors.toMap(r -> (UUID) r[0],
                                r -> new long[]{ (Long) r[1], r[2] != null ? (Long) r[2] : 0L }));

        return folders.stream()
                .map(folder -> {
                    long[] a = agg.getOrDefault(folder.getId(), new long[]{0L, 0L});
                    FolderResponse resp = folderMapper.toResponse(folder);
                    resp.setDocumentCount(Math.toIntExact(a[0]));
                    resp.setFolderSizeBytes(a[1]);
                    return resp;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponse getFolderById(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to access this folder");

        FolderResponse resp = folderMapper.toResponse(folder);
        Object[] agg = documentRepository.countAndSizeByFolderId(folder.getId()).get(0);
        resp.setDocumentCount(Math.toIntExact((Long) agg[0]));
        resp.setFolderSizeBytes(agg[1] != null ? (Long) agg[1] : 0L);
        return resp;
    }

    @Override
    public FolderResponse updateFolder(UUID id, UUID ownerId, FolderUpdateRequest request) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to update this folder");

        if (request.getName() != null && !request.getName().trim().isEmpty()
                && !request.getName().equals(folder.getName())) {
            if (folderRepository.existsByOwnerIdAndNameAndDeletedAtIsNull(ownerId, request.getName()))
                throw new RuntimeException("Folder with this name already exists");
            folder.setName(request.getName());
        }

        if (request.getDescription() != null)
            folder.setDescription(request.getDescription());

        if (request.getSubjectId() != null) {
            Subject subject = subjectRepository.findById(request.getSubjectId())
                    .orElseThrow(() -> new RuntimeException("Subject not found"));
            folder.setSubject(subject);
        }

        Folder updatedFolder = folderRepository.save(folder);
        FolderResponse resp = folderMapper.toResponse(updatedFolder);
        Object[] agg = documentRepository.countAndSizeByFolderId(updatedFolder.getId()).get(0);
        resp.setDocumentCount(Math.toIntExact((Long) agg[0]));
        resp.setFolderSizeBytes(agg[1] != null ? (Long) agg[1] : 0L);
        return resp;
    }

    @Override
    public void deleteFolder(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));

        if (!isAdmin() && !folder.getOwnerId().equals(ownerId))
            throw new AccessDeniedException("You do not have permission to delete this folder");

        LocalDateTime now = LocalDateTime.now();

        // Soft delete all documents in this folder
        documentRepository.softDeleteByFolderId(folder.getId(), now);

        // Soft delete the folder itself
        folder.setDeletedAt(now);
        folderRepository.save(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponse> getTrashFolders(UUID requesterId) {
        List<Folder> folders = isAdmin()
            ? folderRepository.findByDeletedAtIsNotNullOrderByCreatedAtDesc()
            : folderRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByCreatedAtDesc(requesterId);
        return folders.stream().map(folderMapper::toResponse).toList();
    }

    @Override
    public void restoreFolder(UUID id, UUID requesterId) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (folder.getDeletedAt() == null)
            throw new RuntimeException("Folder is not in trash");
        if (!isAdmin() && !folder.getOwnerId().equals(requesterId))
            throw new AccessDeniedException("You do not have permission to restore this folder");

        // Restore documents that were deleted along with this folder
        documentRepository.restoreByFolderId(folder.getId());

        folder.setDeletedAt(null);
        folderRepository.save(folder);
    }

    @Override
    public void permanentDeleteFolder(UUID id, UUID requesterId) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (folder.getDeletedAt() == null)
            throw new RuntimeException("Folder must be in trash before permanent deletion");
        boolean isOwner = folder.getOwnerId().equals(requesterId);
        if (!isOwner && !isAdmin())
            throw new AccessDeniedException("You do not have permission to permanently delete this folder");

        // 2) Load toàn bộ documents trong folder (mọi status, kể cả soft-deleted —
        //    permanent delete folder = xóa thật tất cả, không filter).
        List<Document> docs = documentRepository.findByFolderId(folder.getId());
        List<UUID> docIds = docs.stream().map(Document::getId).toList();

        // 3) Dedup Cloudinary: với mỗi publicId, số reference còn lại sau batch
        //    = count hiện tại − số doc trong batch cùng publicId.
        //    (KHÔNG phụ thuộc flush timing của bulk delete — count TRƯỚC khi xóa)
        Map<String, Long> refsInBatch = docs.stream()
                .map(Document::getPublicId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        List<String> publicIdsNeedDelete = refsInBatch.entrySet().stream()
                .filter(e -> documentRepository.countByPublicId(e.getKey()) - e.getValue() <= 0)
                .map(Map.Entry::getKey)
                .toList();

        // 4) Chat cleanup — batch 1 query (doc chết vĩnh viễn = chat chết)
        if (!docIds.isEmpty()) {
            chatSessionRepository.deleteByDocumentIdIn(docIds);
        }

        // 5) Storage — aggregate TỔNG fileSize, trừ MỘT lần
        long totalSize = documentRepository.sumFileSizeByFolderId(folder.getId());
        if (totalSize > 0) {
            storageQuotaService.subtractUsedBytes(folder.getOwnerId(), totalSize);
        }

        // 6) Document rows — batch delete (an toàn: không lifecycle callback trong project)
        if (!docIds.isEmpty()) {
            documentRepository.deleteAllByIdInBatch(docIds);
        }

        // 7) Folder row
        folderRepository.delete(folder);

        // 8) Cloudinary delete SAU COMMIT — tránh data loss nếu tx rollback
        //    (DB luôn là nguồn sự thật; chấp nhận orphan nếu delete fail sau commit)
        if (!publicIdsNeedDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String publicId : publicIdsNeedDelete) {
                        try {
                            cloudinaryService.delete(publicId);
                        } catch (Exception e) {
                            log.error("Cloudinary cleanup failed after folder delete (orphan accepted): {}", publicId, e);
                        }
                    }
                }
            });
        }
    }

    // ============ HELPER METHODS ============

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
