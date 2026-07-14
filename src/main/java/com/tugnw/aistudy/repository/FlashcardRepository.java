package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {

    List<Flashcard> findByDocumentId(UUID documentId);

    List<Flashcard> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    void deleteByDocumentId(UUID documentId);
    long countByDocumentIdIn(List<UUID> documentIds);
}
