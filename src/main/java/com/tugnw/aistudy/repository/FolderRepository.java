package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID>, JpaSpecificationExecutor<Folder> {
    Optional<Folder> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
    Optional<Folder> findByIdAndDeletedAtIsNull(UUID id);

    List<Folder> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByOwnerIdAndNameAndDeletedAtIsNull(UUID ownerId, String name);

    Optional<Folder> findByOwnerIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID ownerId, String name);
}
