package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {
    List<Share> findByOwnerId(UUID ownerId);
    List<Share> findBySharedAccountId(UUID sharedAccountId);
    Optional<Share> findByFolderIdAndSharedAccountId(UUID folderId, UUID sharedAccountId);
    Optional<Share> findByDocumentIdAndSharedAccountId(UUID documentId, UUID sharedAccountId);
    boolean existsByOwnerIdAndVisibility(UUID ownerId, String visibility);
    boolean existsByDocumentIdAndSharedAccountIdAndRevokedFalse(UUID documentId, UUID sharedAccountId);

    @Query("SELECT s FROM Share s WHERE s.folder.id = :folderId")
    List<Share> findByFolderId(@Param("folderId") UUID folderId);
    
    @Query("SELECT s FROM Share s WHERE s.document.id = :documentId")
    List<Share> findByDocumentId(@Param("documentId") UUID documentId);
    
    Optional<Share> findByShareToken(String shareToken);
}
