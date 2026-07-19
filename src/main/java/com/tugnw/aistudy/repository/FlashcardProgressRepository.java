package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.FlashcardProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlashcardProgressRepository extends JpaRepository<FlashcardProgress, UUID> {

    List<FlashcardProgress> findByAccountId(UUID accountId);

    List<FlashcardProgress> findByFlashcardId(UUID flashcardId);

    Optional<FlashcardProgress> findByFlashcardIdAndAccountId(UUID flashcardId, UUID accountId);
}
