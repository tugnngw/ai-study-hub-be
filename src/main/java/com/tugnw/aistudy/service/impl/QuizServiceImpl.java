package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.domain.dto.quiz.QuestionResponse;
import com.tugnw.aistudy.domain.dto.quiz.QuizResponse;
import com.tugnw.aistudy.domain.dto.quiz.GenerateQuizRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Quiz;
import com.tugnw.aistudy.domain.entity.Question;
import com.tugnw.aistudy.domain.mapper.QuizMapper;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.QuizRepository;
import com.tugnw.aistudy.repository.QuestionRepository;
import com.tugnw.aistudy.service.DocumentSourceResolver;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.QuizService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final RagService ragService;
    private final DocumentRepository documentRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizMapper quizMapper;
    private final DocumentSourceResolver documentSourceResolver;
    private final KnowledgePreparationService knowledgePreparationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public QuizResponse generateQuiz(List<UUID> documentIds, UUID requesterId, GenerateQuizRequest request) throws Exception {
        System.out.println("[LOG - QUIZ] Starting quiz generation for documents: " + documentIds);

        // Resolve and authorize documents
        List<Document> documents = documentSourceResolver.resolveByDocumentIds(documentIds);
        if (documents.isEmpty()) {
            throw new RuntimeException("No accessible documents found for the given IDs");
        }

        authorizeDocuments(documents, requesterId);

        // Prepare merged knowledge from all documents
        String mergedDocumentText = knowledgePreparationService.prepareKnowledge(documents, false);

        if (mergedDocumentText == null || mergedDocumentText.isBlank()) {
            throw new RuntimeException("Unable to extract text from documents.");
        }

        System.out.println("[LOG - QUIZ] Extracted merged text length: " + mergedDocumentText.length());

        UUID firstDocumentId = documents.get(0).getId();
        Quiz quiz = Quiz.builder()
                .documentId(firstDocumentId)
                .title("AI-Generated Quiz from " + documents.size() + " document(s)")
                .generatedByAi(true)
                .createdAt(LocalDateTime.now())
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);
        System.out.println("[LOG - QUIZ] Created quiz with ID: " + savedQuiz.getId());

        List<Question> generatedQuestions = generateQuestionsFromText(
                mergedDocumentText,
                savedQuiz.getId(),
                request.getNumberOfQuestions()
        );
        questionRepository.saveAll(generatedQuestions);

        System.out.println("[LOG - QUIZ] Successfully generated and saved " + generatedQuestions.size() + " questions.");

        List<QuestionResponse> questionResponses = quizMapper.toQuestionResponseList(generatedQuestions);
        return QuizResponse.builder()
                .id(savedQuiz.getId())
                .title(savedQuiz.getTitle())
                .generatedByAi(savedQuiz.getGeneratedByAi())
                .createdAt(savedQuiz.getCreatedAt())
                .questions(questionResponses)
                .build();
    }

    @Override
    public List<QuizResponse> getQuizByDocument(UUID documentId, UUID requesterId) {
        System.out.println("[LOG - QUIZ] Fetching quizzes for document: " + documentId);

        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found or has been deleted."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("You do not have permission to access quizzes for this document.");
        }

        List<Quiz> quizzes = quizRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        List<QuizResponse> responses = new ArrayList<>();

        for (Quiz quiz : quizzes) {
            List<Question> questions = questionRepository.findByQuizIdOrderByCreatedAtAsc(quiz.getId());
            List<QuestionResponse> questionResponses = quizMapper.toQuestionResponseList(questions);
            responses.add(QuizResponse.builder()
                    .id(quiz.getId())
                    .title(quiz.getTitle())
                    .generatedByAi(quiz.getGeneratedByAi())
                    .createdAt(quiz.getCreatedAt())
                    .questions(questionResponses)
                    .build());
        }

        return responses;
    }

    private List<Question> generateQuestionsFromText(String documentText, Long quizId, Integer numberOfQuestions) throws Exception {
        String prompt = String.format(
                "Based on the following document content, generate exactly %d multiple-choice questions in JSON format. " +
                "Each question should have: 'content' (the question), 'optionA', 'optionB', 'optionC', 'optionD' (the answer choices), " +
                "and 'correctAnswer' (A, B, C, or D indicating the correct option). " +
                "Return a valid JSON array with objects containing these keys only.\n\n" +
                "Document:\n%s\n\n" +
                "Return ONLY valid JSON array, no markdown formatting, no code blocks.",
                numberOfQuestions,
                documentText.substring(0, Math.min(5000, documentText.length()))
        );

        String aiResponse = ragService.generateContent(prompt);
        System.out.println("[LOG - QUIZ] Gemini response received, parsing questions...");

        return parseQuestionsFromResponse(aiResponse, quizId);
    }

    private List<Question> parseQuestionsFromResponse(String jsonResponse, Long quizId) throws Exception {
        List<Question> questions = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String content = item.has("content") ? item.get("content").asText().trim() : "";
                    String optionA = item.has("optionA") ? item.get("optionA").asText().trim() : "";
                    String optionB = item.has("optionB") ? item.get("optionB").asText().trim() : "";
                    String optionC = item.has("optionC") ? item.get("optionC").asText().trim() : "";
                    String optionD = item.has("optionD") ? item.get("optionD").asText().trim() : "";
                    String correctAnswer = item.has("correctAnswer") ? item.get("correctAnswer").asText().trim() : "";

                    if (!content.isBlank() && !optionA.isBlank() && !optionB.isBlank() &&
                            !optionC.isBlank() && !optionD.isBlank() && !correctAnswer.isBlank()) {
                        Question question = Question.builder()
                                .quizId(quizId)
                                .content(content)
                                .optionA(optionA)
                                .optionB(optionB)
                                .optionC(optionC)
                                .optionD(optionD)
                                .correctAnswer(correctAnswer)
                                .createdAt(LocalDateTime.now())
                                .build();
                        questions.add(question);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LOG - QUIZ ERROR] Failed to parse questions: " + e.getMessage());
            throw new RuntimeException("Failed to parse AI-generated questions.", e);
        }
        return questions;
    }

    private void authorizeDocuments(List<Document> documents, UUID requesterId) {
        for (Document doc : documents) {
            if (!isAdmin() && !doc.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("Access denied to document: " + doc.getId());
            }
        }
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}