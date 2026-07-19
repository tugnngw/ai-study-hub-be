package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.flashcard.FlashcardResponse;
import com.tugnw.aistudy.domain.dto.flashcard.GenerateFlashcardsRequest;

import java.util.List;
import java.util.UUID;

public interface FlashcardService {

    List<FlashcardResponse> generateFlashcards(UUID requesterId, GenerateFlashcardsRequest request) throws Exception;

    List<FlashcardResponse> getFlashcardsByDocument(UUID documentId, UUID requesterId);
}
