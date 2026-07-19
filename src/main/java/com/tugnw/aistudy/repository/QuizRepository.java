package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByDocumentId(UUID documentId);

    List<Quiz> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
