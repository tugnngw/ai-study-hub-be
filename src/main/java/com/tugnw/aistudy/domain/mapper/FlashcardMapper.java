package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.entity.Flashcard;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FlashcardMapper {

    FlashcardResponse toResponse(Flashcard flashcard);

    List<FlashcardResponse> toResponseList(List<Flashcard> flashcards);
}
