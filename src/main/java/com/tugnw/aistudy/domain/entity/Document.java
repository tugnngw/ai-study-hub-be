package com.tugnw.aistudy.domain.entity;

import com.tugnw.aistudy.domain.enums.AiProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 50)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "cloudinary_url", length = 500)
    private String cloudinaryUrl;

    @Column(name = "public_id", length = 255)
    private String publicId;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(length = 255)
    private String checksum;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", length = 50, nullable = false)
    @Builder.Default
    private AiProcessingStatus aiStatus = AiProcessingStatus.NOT_STARTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "flashcard_generations")
    @Builder.Default
    private Integer flashcardGenerations = 0;

    @Column(name = "quiz_generations")
    @Builder.Default
    private Integer quizGenerations = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}