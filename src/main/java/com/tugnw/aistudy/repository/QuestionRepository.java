package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByQuizIdOrderByCreatedAtAsc(UUID quizId);
    long countByQuizIdIn(List<UUID> quizIds);

    @Query("SELECT COUNT(DISTINCT q.quizId) FROM Question q WHERE q.quizId IN :quizIds")
    long countDistinctQuizIdByQuizIdIn(@Param("quizIds") List<UUID> quizIds);
}
