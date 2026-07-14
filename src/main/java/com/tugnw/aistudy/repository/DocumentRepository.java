package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Document> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    List<Document> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

    List<Document> findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID folderId);

    List<Document> findByFolderIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(UUID folderId, String status);

    long countByFolderIdAndDeletedAtIsNull(UUID folderId);

    Long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    // Kiểm tra storage usage
    boolean existsByOwnerIdAndChecksumAndDeletedAtIsNull(UUID ownerId, String checksum);

    List<Document> findByStatusAndDeletedAtIsNull(String status);

    List<Document> findByDeletedAtIsNotNull();
    List<Document> findByOwnerIdAndDeletedAtIsNotNullOrderByCreatedAtDesc(UUID ownerId);
    List<Document> findByDeletedAtIsNotNullOrderByCreatedAtDesc();

    List<Document> findAllByDeletedAtIsNull();

    List<Document> findAllBy();
    long countByOwnerIdAndSummaryIsNotNull(UUID ownerId);
    
    @Query("SELECT d.id FROM Document d WHERE d.ownerId = :ownerId")
    List<UUID> findAllIdsByOwnerId(@Param("ownerId") UUID ownerId);
}