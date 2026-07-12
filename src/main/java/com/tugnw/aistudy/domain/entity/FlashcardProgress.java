package com.tugnw.aistudy.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "flashcard_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"flashcard_id", "account_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flashcard_id", nullable = false)
    private UUID flashcardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "new";

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "next_review_at")
    private LocalDateTime nextReviewAt;
}
