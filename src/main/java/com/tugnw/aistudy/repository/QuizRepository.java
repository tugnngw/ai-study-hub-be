package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findByDocumentId(UUID documentId);

    List<Quiz> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    @Query("SELECT q.id FROM Quiz q WHERE q.documentId IN :documentIds")
    List<UUID> findAllIdsByDocumentIds(@Param("documentIds") List<UUID> documentIds);

    @Query("SELECT q.id FROM Quiz q WHERE q.documentId = :documentId")
    List<UUID> findIdsByDocumentId(@Param("documentId") UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
