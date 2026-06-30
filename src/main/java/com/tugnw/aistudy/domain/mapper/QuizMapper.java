package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.quiz.QuestionResponse;
import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.entity.Question;
import com.tugnw.aistudy.domain.entity.Quiz;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuizMapper {

    QuizResponse toResponse(Quiz quiz);

    List<QuizResponse> toResponseList(List<Quiz> quizzes);

    QuestionResponse toQuestionResponse(Question question);

    List<QuestionResponse> toQuestionResponseList(List<Question> questions);
}
