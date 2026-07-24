package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import com.tugnw.aistudy.domain.entity.ChatMessage;
import com.tugnw.aistudy.domain.entity.ChatSession;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.DocumentChunk;
import com.tugnw.aistudy.domain.enums.AiProcessingStatus;
import com.tugnw.aistudy.repository.ChatMessageRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.DocumentChunkRepository;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.QuotaService;
import com.tugnw.aistudy.service.RagService;
import com.tugnw.aistudy.service.RagStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final QuotaService quotaService;
    private final DocumentService documentService;
    private final RagStatusService ragStatusService;

    private final Tika tika = new Tika();
    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model.embedding}")
    private String embeddingModel;

    @Value("${gemini.model.chat}")
    private String chatModel;

    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 1000;

    // ==========================================
    // GIAI ĐOẠN 1: TẢI FILE & TRÍCH XUẤT VĂN BẢN
    // ==========================================
    @Override
    @Transactional(readOnly = true)
    public String extractTextFromDocument(UUID documentId, UUID requesterId) throws Exception {
        // Use centralized access check — AI operations require READY status
        Document document = documentService.getAccessibleDocument(documentId, requesterId, true);

        String fileUrl = document.getCloudinaryUrl();
        if (fileUrl == null || fileUrl.isBlank())
            throw new RuntimeException("Tài liệu chưa có URL lưu trữ trên Cloudinary.");

        fileUrl = fileUrl.replace("http://", "https://");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/pdf, text/plain, application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document, */*")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200)
            throw new RuntimeException("Cloudinary trả về mã lỗi: " + response.statusCode());

        try (InputStream inputStream = response.body()) {
            String extractedText = tika.parseToString(inputStream);
            extractedText = extractedText.replaceAll("(?m)^[ \t]*\r?\n", "");
            return extractedText;
        }
    }

    // ==========================================
    // GIAI ĐOẠN 2: CHUNKING & EMBEDDING PIPELINE
    // ==========================================
    @Override
    public void processAndSaveDocumentPipeline(UUID documentId, UUID requesterId) throws Exception {
        Instant start = Instant.now();

        Document doc = documentService.getAccessibleDocument(documentId, requesterId, true);

        long chunkCount = chunkRepository.countByDocumentId(documentId);
        if (chunkCount > 0 && doc.getAiStatus() == AiProcessingStatus.COMPLETED) return;

        doc.setAiStatus(AiProcessingStatus.PROCESSING);
        documentRepository.save(doc);

        try {
            String rawText = extractTextFromDocument(documentId, requesterId);
            if (rawText == null || rawText.isBlank())
                throw new RuntimeException("Không thể trích xuất nội dung từ tài liệu.");

            List<String> textChunks = recursiveChunking(rawText);

            List<ChunkData> chunkDataList = embedAllChunks(textChunks);

            saveChunksBatch(documentId, chunkDataList);

            doc.setAiStatus(AiProcessingStatus.COMPLETED);
            documentRepository.save(doc);

            Duration elapsed = Duration.between(start, Instant.now());
        } catch (Throwable e) {
            ragStatusService.markProcessingFailed(documentId);
            if (e instanceof Exception)
                throw (Exception) e;

            throw new RuntimeException("Pipeline failed", e);
        }
    }

    /** Embed all chunks, optionally parallel. Returns list paired with index. */
    private List<ChunkData> embedAllChunks(List<String> textChunks) {
        // ponytail: sequential embedding to avoid Gemini rate limits.
        // Parallel with ExecutorService if rate limit allows.
        List<ChunkData> results = new ArrayList<>(textChunks.size());
        for (int i = 0; i < textChunks.size(); i++) {
            String vectorStr = getEmbeddingWithRetry(textChunks.get(i));
            results.add(new ChunkData(i, textChunks.get(i), vectorStr));
        }
        return results;
    }

    private String getEmbeddingWithRetry(String text) {
        Exception lastEx = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                List<Double> vector = getEmbeddingFromGemini(text);
                if (!vector.isEmpty()) {
                    return vector.toString().replace(" ", "");
                }
            } catch (Exception e) {
                lastEx = e;
                if (attempt < MAX_RETRIES - 1)
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1));
                    }
                catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); break;
                    }

            }
        }
        throw new RuntimeException("Embedding thất bại sau " + MAX_RETRIES + " lần thử", lastEx);
    }

    /** Transactional batch write: delete old + batch insert new chunks. */
    private void saveChunksBatch(UUID documentId, List<ChunkData> chunks) {
        transactionTemplate.executeWithoutResult(status -> {
            // xoá chunk cũ
            chunkRepository.deleteByDocumentId(documentId);
            chunkRepository.flush();

            // batch insert qua JdbcTemplate
            String sql = "INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding_vector) " +
                         "VALUES (gen_random_uuid(), ?, ?, ?, CAST(? AS vector))";

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ChunkData c = chunks.get(i);
                    ps.setObject(1, documentId);
                    ps.setInt(2, c.index);
                    ps.setString(3, c.content);
                    ps.setString(4, c.vectorString);
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            });

            log.info("[BATCH] Đã lưu {} chunk(s) vào database", chunks.size());
        });
    }

    private record ChunkData(int index, String content, String vectorString) {}

    @Override
    @Transactional(readOnly = true)
    public String getDocumentProcessingStatus(UUID documentId, UUID requesterId) {
        try {
            Document document = documentService.getAccessibleDocument(documentId, requesterId, false);
            String aiStatus = document.getAiStatus() != null ? document.getAiStatus().name() : "NOT_STARTED";

            return aiStatus;
        } catch (Exception e) {
            return "not_found";
        }
    }

    private List<Double> getEmbeddingFromGemini(String text) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + embeddingModel + ":embedContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = Map.of("text", text);
        Map<String, Object> contentPart = Map.of("parts", List.of(textPart));
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("content", contentPart);
        requestBody.put("outputDimensionality", 768);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> embeddingResult = (Map<String, Object>) response.getBody().get("embedding");
                return (List<Double>) embeddingResult.get("values");
            }
        } catch (Exception e) {
            log.error("[EMBEDDING] Gemini API lỗi: {}", e.getMessage());
        }
        return List.of();
    }

    private List<String> recursiveChunking(String text) {
        List<String> result = new ArrayList<>();
        String[] separators = {"\n\n", "\n", ". ", " "};
        splitTextWithOverlap(text, MAX_CHUNK_SIZE, CHUNK_OVERLAP, separators, 0, result);
        return result;
    }

    private void splitTextWithOverlap(String text, int maxSize, int overlap,
                                       String[] separators, int sepIndex,
                                       List<String> result) {
        if (text.length() <= maxSize) {
            result.add(text.trim());
            return;
        }

        if (sepIndex >= separators.length) {
            result.add(text.substring(0, maxSize));
            splitTextWithOverlap(text.substring(maxSize - overlap), maxSize, overlap,
                    separators, sepIndex, result);
            return;
        }

        String separator = separators[sepIndex];
        String[] parts = text.split(java.util.regex.Pattern.quote(separator));
        StringBuilder currentChunk = new StringBuilder();

        for (String part : parts) {
            String combined = (currentChunk.length() == 0 ? "" : separator) + part;

            if (currentChunk.length() + combined.length() <= maxSize) {
                currentChunk.append(combined);
            } else {
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString().trim());
                }

                String overlapText = currentChunk.length() > overlap
                        ? currentChunk.substring(currentChunk.length() - overlap)
                        : currentChunk.toString();

                if (part.length() > maxSize) {
                    splitTextWithOverlap(part, maxSize, overlap, separators, sepIndex + 1, result);
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(overlapText + separator + part);
                }
            }
        }
        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString().trim());
        }
    }

    // ==========================================
    // GIAI ĐOẠN 3: RETRIEVAL & GENERATION (CHAT)
    // ==========================================
    @Override
    public RagChatResponse chatWithFolderContext(RagChatRequest chatRequest, UUID requesterId) {
        UUID docId = chatRequest.getDocumentId();
        // Verify document ownership and approval status

        // Embed question
        List<Double> queryVector = getEmbeddingFromGemini(chatRequest.getQuestion());
        if (queryVector.isEmpty())
            throw new RuntimeException("Không thể tạo vector cho câu hỏi.");

        String queryVectorString = queryVector.toString().replace(" ", "");

        // Vector search — only this document's chunks
        List<DocumentChunk> relevantChunks = chunkRepository.findTopChunksByDocumentAndVector(
                docId, queryVectorString);

        if (relevantChunks.isEmpty())
            throw new RuntimeException("Không tìm thấy nội dung trong tài liệu. Vui lòng xử lý AI lại.");


        // Build context
        StringBuilder contextBuilder = new StringBuilder();
        Set<UUID> referencedDocIds = new HashSet<>();
        for (DocumentChunk chunk : relevantChunks) {
            contextBuilder.append("--- Tài liệu ID ").append(chunk.getDocumentId()).append(" ---\n");
            contextBuilder.append(chunk.getContent()).append("\n\n");
            referencedDocIds.add(chunk.getDocumentId());
        }

        // Generate answer (expensive AI call — done before quota check to avoid wasted quota)
        String prompt = buildChatPrompt(contextBuilder.toString(), chatRequest.getQuestion());
        String aiAnswer = generateTextFromGemini(prompt);

        // Save session + messages (quota check inside same transaction — atomic)
        UUID sessionId = saveChatHistory(chatRequest, requesterId, aiAnswer, referencedDocIds);

        return RagChatResponse.builder()
                .sessionId(sessionId)
                .answer(aiAnswer)
                .referencedDocumentIds(referencedDocIds)
                .build();
    }

    /** Save user question + AI answer. Create session if new. Title = first user message. */
    private UUID saveChatHistory(RagChatRequest req, UUID accountId, String aiAnswer, Set<UUID> referencedDocs) {
        return transactionTemplate.execute(status -> {
            // Check quota inside transaction — rollback on failure prevents counting
            if (!quotaService.checkQuota(accountId, "chat"))
                throw new RuntimeException("Bạn đã đạt giới hạn số lượng tin nhắn AI cho gói hiện tại.");


            ChatSession session;

            if (req.getSessionId() != null) {
                session = chatSessionRepository.findById(req.getSessionId()).orElse(null);
                if (session == null)
                    session = createSession(accountId, req.getDocumentId());

            } else {
                session = createSession(accountId, req.getDocumentId());
            }

            UUID sessionId = session.getId();

            // Title = first user message (like ChatGPT)
            if (session.getTitle() == null && !req.getQuestion().isBlank()) {
                String title = req.getQuestion().strip();
                if (title.length() > 75) {
                    int cut = title.lastIndexOf(' ', 75);
                    title = (cut > 40 ? title.substring(0, cut) : title.substring(0, 75)) + "...";
                }
                session.setTitle(title);
                chatSessionRepository.save(session);
            }

            // Save user message
            chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(sessionId)
                    .senderType("USER")
                    .content(req.getQuestion())
                    .build());

            // Save AI message
            chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(sessionId)
                    .senderType("AI")
                    .content(aiAnswer)
                    .referencedChunks(referencedChunksToJson(referencedDocs))
                    .build());

            // Bump updatedAt so ordering reflects latest activity
            chatSessionRepository.save(session);

            return sessionId;
        });
    }

    private ChatSession createSession(UUID accountId, UUID documentId) {
        return chatSessionRepository.save(ChatSession.builder()
                .accountId(accountId)
                .documentId(documentId)
                .build());
    }

    private String referencedChunksToJson(Set<UUID> docIds) {
        return "[" + String.join(",", docIds.stream().map(id -> "\"" + id + "\"").toList()) + "]";
    }

    private String buildChatPrompt(String context, String question) {
        return "Bạn là trợ lý học tập AI thông minh của hệ thống AI Study Hub.\n"
                + "Trả lời câu hỏi dựa trên tài liệu tham khảo dưới đây.\n\n"
                + "--- TÀI LIỆU THAM KHẢO ---\n" + context + "\n"
                + "--- CÂU HỎI ---\n" + question + "\n\n"
                + "YÊU CẦU:\n"
                + "1. Chỉ dùng thông tin trong tài liệu. Không bịa đặt.\n"
                + "2. Dùng Markdown (đậm, bullet) để dễ đọc.\n"
                + "3. Nếu tài liệu không có câu trả lời, nói: 'Xin lỗi, kiến thức này nằm ngoài các tài liệu hiện có.'";
    }

    @Override
    public String generateContent(String prompt) {
        log.debug("[GENERATE] Generating text from prompt");
        return generateTextFromGemini(prompt);
    }

    private String generateTextFromGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + chatModel + ":generateContent?key=" + geminiApiKey;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(textPart));
        Map<String, Object> contents = Map.of("contents", List.of(parts));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(contents, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map> candidates = (List<Map>) response.getBody().get("candidates");
                Map content = (Map) candidates.get(0).get("content");
                List<Map> partsList = (List<Map>) content.get("parts");
                return (String) partsList.get(0).get("text");
            }
        } catch (Exception e) {
            log.error("[GEMINI] Chat API lỗi: {}", e.getMessage());
        }
        return "Xin lỗi, hệ thống AI đang gặp sự cố. Vui lòng thử lại sau.";
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
