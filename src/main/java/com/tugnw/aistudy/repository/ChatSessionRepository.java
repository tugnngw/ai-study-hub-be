package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByAccountIdAndDocumentIdOrderByUpdatedAtDesc(UUID accountId, UUID documentId);

    @Query("SELECT cs.id FROM ChatSession cs WHERE cs.documentId IN :documentIds")
    List<UUID> findSessionIdsByDocumentIds(@Param("documentIds") List<UUID> documentIds);

    List<ChatSession> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
