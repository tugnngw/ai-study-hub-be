package com.tugnw.aistudy.repository;

import com.tugnw.aistudy.domain.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByQuizIdOrderByCreatedAtAsc(Long quizId);
}
