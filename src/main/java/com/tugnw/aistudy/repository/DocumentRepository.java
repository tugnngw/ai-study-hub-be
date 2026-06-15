package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    List<Document> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

    List<Document> findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID folderId);

    Long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    // Kiểm tra storage usage
    boolean existsByOwnerIdAndChecksumAndDeletedAtIsNull(UUID ownerId, String checksum);
}