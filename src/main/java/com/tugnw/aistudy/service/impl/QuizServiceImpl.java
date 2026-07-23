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
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.QuizService;
import com.tugnw.aistudy.service.RagService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final RagService ragService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizMapper quizMapper;
    private final KnowledgePreparationService knowledgePreparationService;
    private final QuotaService quotaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager entityManager;

    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_OPTION_LENGTH = 500;

    @Override
    @Transactional
    public QuizResponse generateQuiz(UUID documentId, UUID requesterId, GenerateQuizRequest request) throws Exception {
        log.info("[LOG - QUIZ] Starting quiz generation for document: " + documentId);

        Document document = documentService.getAccessibleDocument(documentId, requesterId, true);

        // Check quota trước khi gọi AI
        if (!quotaService.checkQuota(requesterId, "question")) {
            throw new RuntimeException("Bạn đã đạt giới hạn số lần tạo câu hỏi cho gói hiện tại. Vui lòng nâng cấp gói để tiếp tục sử dụng.");
        }

        String documentText = ragService.extractTextFromDocument(document.getId(), requesterId);

        if (documentText == null || documentText.isBlank()) {
            throw new RuntimeException("Unable to extract text from document.");
        }

        log.info("[LOG - QUIZ] Extracted text length: " + documentText.length());

        // Bước 1: Gọi AI trước — chưa persist gì cả
        UUID quizId = UUID.randomUUID();
        List<Question> parsed = generateQuestionsFromText(documentText, quizId, request.getNumberOfQuestions());
        int rawCount = parsed.size();

        // Bước 2: Validate từng câu, skip câu lỗi
        List<Question> valid = validateQuestions(parsed);
        int savedCount = valid.size();

        // Bước 3: 0 câu hợp lệ hoặc quá ít → throw, giữ nguyên quiz cũ
        int requested = request.getNumberOfQuestions();
        if (valid.isEmpty()) {
            log.error("[LOG - QUIZ] All {} parsed questions failed validation.", rawCount);
            throw new RuntimeException("AI không tạo được câu hỏi hợp lệ từ tài liệu này. Vui lòng thử lại.");
        }
        if ((double) savedCount / requested < 0.3) {
            log.error("[LOG - QUIZ] Only {}/{} questions valid, below 30% threshold.", savedCount, requested);
            throw new RuntimeException("Chất lượng câu hỏi tạo ra quá thấp (" + savedCount + "/" + requested + " hợp lệ). Vui lòng thử lại.");
        }

        // Bước 4: Xóa quiz cũ — dùng JPQL bulk delete tránh optimistic lock
        List<UUID> oldQuizIds = quizRepository.findIdsByDocumentId(documentId);
        if (!oldQuizIds.isEmpty()) {
            questionRepository.deleteByQuizIdIn(oldQuizIds);
            quizRepository.deleteByDocumentId(documentId);
            entityManager.flush();
            // Xóa persistence context để tránh stale state
            entityManager.clear();
            log.info("[LOG - QUIZ] Deleted {} existing quizzes.", oldQuizIds.size());
        }

        // Bước 5: Save quiz mới — không set ID, để Hibernate tự gen
        Quiz quiz = Quiz.builder()
                .documentId(document.getId())
                .title("AI-Generated Quiz")
                .generatedByAi(true)
                .createdAt(LocalDateTime.now())
                .build();

        entityManager.persist(quiz);
        // Gán quizId cho questions sau khi đã persist
        UUID savedQuizId = quiz.getId();
        for (Question q : valid) {
            q.setQuizId(savedQuizId);
            entityManager.persist(q);
        }
        entityManager.flush();

        // Increment generation counter
        document.setQuizGenerations(document.getQuizGenerations() == null ? 1 : document.getQuizGenerations() + 1);
        documentRepository.save(document);

        String message = buildResultMessage(savedCount, requested, rawCount);
        log.info("[LOG - QUIZ] {}", message);

        List<QuestionResponse> questionResponses = quizMapper.toQuestionResponseList(valid);
        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .generatedByAi(quiz.getGeneratedByAi())
                .createdAt(quiz.getCreatedAt())
                .questions(questionResponses)
                .build();
    }

    @Override
    public List<QuizResponse> getQuizByDocument(UUID documentId, UUID requesterId) {
        log.info("[LOG - QUIZ] Fetching quizzes for document: " + documentId);

        Document document = documentService.getAccessibleDocument(documentId, requesterId, true);

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

    private List<Question> generateQuestionsFromText(String documentText, UUID quizId, Integer numberOfQuestions) throws Exception {
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
        log.info("[LOG - QUIZ] Gemini response received, parsing questions...");

        return parseQuestionsFromResponse(aiResponse, quizId);
    }

    private List<Question> parseQuestionsFromResponse(String jsonResponse, UUID quizId) throws Exception {
        List<Question> questions = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String content = item.has("content") ? item.get("content").asText().trim() : "";

                    // Hỗ trợ cả format options:[...] mới và optionA/B/C/D cũ
                    String optionA, optionB, optionC, optionD;
                    if (item.has("options") && item.get("options").isArray() && item.get("options").size() == 4) {
                        JsonNode opts = item.get("options");
                        optionA = opts.get(0).asText().trim();
                        optionB = opts.get(1).asText().trim();
                        optionC = opts.get(2).asText().trim();
                        optionD = opts.get(3).asText().trim();
                    } else {
                        optionA = item.has("optionA") ? item.get("optionA").asText().trim() : "";
                        optionB = item.has("optionB") ? item.get("optionB").asText().trim() : "";
                        optionC = item.has("optionC") ? item.get("optionC").asText().trim() : "";
                        optionD = item.has("optionD") ? item.get("optionD").asText().trim() : "";
                    }

                    String correctAnswer = item.has("correctAnswer") ? item.get("correctAnswer").asText().trim().toUpperCase() : "";

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
            log.error("[LOG - QUIZ ERROR] Failed to parse questions: " + e.getMessage());
            throw new RuntimeException("Failed to parse AI-generated questions.", e);
        }
        return questions;
    }

    private List<Question> validateQuestions(List<Question> questions) {
        List<Question> valid = new ArrayList<>();
        for (Question q : questions) {
            String content = q.getContent();
            String ca = q.getCorrectAnswer();
            String[] opts = {q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()};

            boolean skip = false;
            String reason = null;

            if (content.length() > MAX_QUESTION_LENGTH) {
                skip = true;
                reason = "content too long (" + content.length() + " > " + MAX_QUESTION_LENGTH + ")";
            } else if (Stream.of(opts).anyMatch(o -> o.length() > MAX_OPTION_LENGTH)) {
                skip = true;
                reason = "option too long (> " + MAX_OPTION_LENGTH + ")";
            } else if (Stream.of(opts).filter(String::isBlank).count() > 0) {
                skip = true;
                reason = "one or more options blank after trim";
            } else if (!ca.matches("[A-D]")) {
                skip = true;
                reason = "invalid correctAnswer: \"" + ca + "\"";
            } else {
                int correctIdx = ca.charAt(0) - 'A';
                if (opts[correctIdx].isBlank()) {
                    skip = true;
                    reason = "correctAnswer points to empty option";
                }
            }

            if (!skip && valid.stream().anyMatch(v -> v.getContent().equalsIgnoreCase(content))) {
                skip = true;
                reason = "duplicate content in batch";
            }

            if (skip) {
                log.warn("[LOG - QUIZ] Skipped question: {} — content=\"{}\"", reason, truncate(content, 60));
            } else {
                valid.add(q);
            }
        }
        return valid;
    }

    private String buildResultMessage(int saved, int requested, int raw) {
        if (saved == requested) {
            return "Đã tạo thành công " + saved + " câu hỏi.";
        }
        if (saved < 1) {
            return "Không tạo được câu hỏi nào. Vui lòng thử lại.";
        }
        if (saved == raw) {
            return "Yêu cầu " + requested + " câu hỏi, AI tạo được " + raw + ". Đã lưu " + saved + " câu hỏi.";
        }
        return "Đã tạo " + saved + "/" + requested + " câu hỏi. "
                + (raw - saved) + " câu bị lỗi format hoặc trùng lặp đã được bỏ qua.";
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
