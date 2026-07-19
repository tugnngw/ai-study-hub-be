package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByQuizIdOrderByCreatedAtAsc(UUID quizId);
    long countByQuizIdIn(List<UUID> quizIds);
}
