package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.entity.Folder;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.DocumentChunkRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final FolderRepository folderRepository;

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
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ==========================================
    // GIAI ĐOẠN 1: TẢI FILE & TRÍCH XUẤT VĂN BẢN
    // ==========================================
    @Override
    public String extractTextFromDocument(UUID documentId, UUID requesterId) throws Exception {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Tài liệu không tồn tại hoặc đã bị xóa."));

        if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        String fileUrl = document.getCloudinaryUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new RuntimeException("Tài liệu chưa có URL lưu trữ trên Cloudinary.");
        }

        if (fileUrl.startsWith("http://")) {
            fileUrl = fileUrl.replace("http://", "https://");
        }

        System.out.println("[LOG - EXTRACT] 2. Bắt đầu tải file từ Cloudinary: " + fileUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/pdf, text/plain, application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document, */*")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        System.out.println("[LOG - EXTRACT] 3. Cloudinary trả về Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Lỗi khi tải file từ Cloudinary. Mã trạng thái: " + response.statusCode());
        }

        try (InputStream inputStream = response.body()) {
            String extractedText = tika.parseToString(inputStream);
            extractedText = extractedText.replaceAll("(?m)^[ \t]*\r?\n", "");
            System.out.println("[LOG - EXTRACT] 4. Tika bóc tách thành công: " + extractedText.length() + " ký tự.");
            return extractedText;
        } catch (Exception e) {
            System.err.println("[LOG - EXTRACT LỖI] Lỗi bóc tách chữ: " + e.getMessage());
            throw e;
        }
    }

    // ==========================================
    // GIAI ĐOẠN 2: CHUNKING & EMBEDDING PIPELINE
    // ==========================================
    @Override
    @Transactional
    public void processAndSaveDocumentPipeline(UUID documentId, UUID requesterId) throws Exception {
        System.out.println("\n========== BẮT ĐẦU PIPELINE RAG ==========");
        Document docStatus = documentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (docStatus != null) {
            if (!isAdmin() && !docStatus.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("You do not have permission to process this document");
            }
            docStatus.setStatus("PROCESSING");
            documentRepository.save(docStatus);
        }

        try {
            String rawText = extractTextFromDocument(documentId, requesterId);
            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("Không thể bóc tách nội dung văn bản từ tài liệu này.");
            }

            List<String> textChunks = recursiveChunking(rawText);
            System.out.println("[LOG - PIPELINE] Băm được tổng cộng: " + textChunks.size() + " chunks.");

            for (int i = 0; i < textChunks.size(); i++) {
                System.out.println("[LOG - PIPELINE] ---> Đang xử lý chunk thứ " + i);
                String chunkContent = textChunks.get(i);

                List<Double> vector = getEmbeddingFromGemini(chunkContent);

                if (!vector.isEmpty()) {
                    System.out.println("[LOG - PIPELINE]      Đã lấy Vector thành công (Size: " + vector.size() + "). Chuẩn bị lưu DB...");

                    // SỬA LỖI ĐỊNH DẠNG VECTOR Ở ĐÂY (CỰC KỲ QUAN TRỌNG)
                    String vectorString = vector.toString().replace(" ", "");

                    try {
                        chunkRepository.saveChunkWithVector(documentId, i, chunkContent, vectorString);
                        System.out.println("[LOG - PIPELINE]      ✅ Lưu DATABASE thành công chunk: " + i);
                    } catch (Exception dbErr) {
                        System.err.println("[LOG - LỖI DB] ❌ KHÔNG THỂ LƯU CHUNK " + i + " VÀO DATABASE!");
                        System.err.println("Chi tiết lỗi: " + dbErr.getMessage());
                        dbErr.printStackTrace(); // In toàn bộ lỗi ra console
                        throw dbErr; // Bắt buộc throw để Rollback
                    }
                } else {
                    throw new RuntimeException("Thất bại trong việc tạo vector ở chunk thứ: " + i);
                }
            }

            if (docStatus != null) {
                docStatus.setStatus("COMPLETED");
                documentRepository.save(docStatus);
                System.out.println("========== PIPELINE HOÀN TẤT THÀNH CÔNG ==========\n");
            }
        } catch (Exception e) {
            System.err.println("[LOG - PIPELINE LỖI] Toàn bộ tiến trình thất bại: " + e.getMessage());
            if (docStatus != null) {
                docStatus.setStatus("failed");
                documentRepository.save(docStatus);
            }
            throw e;
        }
    }

    @Override
    public void processFolderPipeline(UUID folderId, UUID requesterId) throws Exception {
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (!isAdmin() && !folder.getOwnerId().equals(requesterId)) {
            throw new RuntimeException("You do not have permission to process this folder");
        }
        List<Document> documents = documentRepository.findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);
        for (Document doc : documents) {
            if ("pending".equalsIgnoreCase(doc.getStatus()) || "ready".equalsIgnoreCase(doc.getStatus()) || "failed".equalsIgnoreCase(doc.getStatus()) || "COMPLETED".equalsIgnoreCase(doc.getStatus())) {
                try {
                    processAndSaveDocumentPipeline(doc.getId(), requesterId);
                } catch (Exception e) {
                    System.err.println("Pipeline failed for document " + doc.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public String getDocumentProcessingStatus(UUID documentId, UUID requesterId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (document != null) {
            if (!isAdmin() && !document.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("You do not have permission to view this document");
            }
            return document.getStatus();
        }
        return "not_found";
    }

    private List<Double> getEmbeddingFromGemini(String text) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + embeddingModel + ":embedContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", text);

        Map<String, Object> contentPart = new HashMap<>();
        contentPart.put("parts", List.of(textPart));

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
            System.err.println("[LOG - LỖI API] Lỗi nghiêm trọng khi gọi Gemini Embedding: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private List<String> recursiveChunking(String text) {
        String[] separators = {"\n\n", "\n", ". ", " "};
        return splitTextWithOverlap(text, MAX_CHUNK_SIZE, CHUNK_OVERLAP, separators, 0);
    }

    private List<String> splitTextWithOverlap(String text, int maxSize, int overlap, String[] separators, int sepIndex) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxSize) {
            chunks.add(text.trim());
            return chunks;
        }

        if (sepIndex >= separators.length) {
            chunks.add(text.substring(0, maxSize));
            chunks.addAll(splitTextWithOverlap(text.substring(maxSize - overlap), maxSize, overlap, separators, sepIndex));
            return chunks;
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
                    chunks.add(currentChunk.toString().trim());
                }

                String overlapText = currentChunk.length() > overlap
                        ? currentChunk.substring(currentChunk.length() - overlap)
                        : currentChunk.toString();

                if (part.length() > maxSize) {
                    chunks.addAll(splitTextWithOverlap(part, maxSize, overlap, separators, sepIndex + 1));
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(overlapText + separator + part);
                }
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    // ==========================================
    // GIAI ĐOẠN 3: RETRIEVAL & GENERATION (CHAT)
    // ==========================================
    @Override
    public RagChatResponse chatWithFolderContext(RagChatRequest chatRequest, UUID requesterId) throws Exception {
        if (chatRequest.getDocumentId() != null) {
            Document document = documentRepository.findByIdAndDeletedAtIsNull(chatRequest.getDocumentId()).orElse(null);
            if (document != null && !isAdmin() && !document.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("You do not have permission to access this document");
            }
        } else if (chatRequest.getFolderId() != null) {
            Folder folder = folderRepository.findByIdAndDeletedAtIsNull(chatRequest.getFolderId()).orElse(null);
            if (folder != null && !isAdmin() && !folder.getOwnerId().equals(requesterId)) {
                throw new RuntimeException("You do not have permission to access this folder");
            }
        }
        List<Double> queryVector = getEmbeddingFromGemini(chatRequest.getQuestion());
        if (queryVector.isEmpty()) {
            throw new RuntimeException("Không thể tạo vector cho câu hỏi.");
        }

        String queryVectorString = queryVector.toString().replace(" ", "");

        List<com.tugnw.aistudy.domain.entity.DocumentChunk> relevantChunks;
        if (chatRequest.getDocumentId() != null) {
            relevantChunks = chunkRepository.findTopChunksByDocumentAndVector(chatRequest.getDocumentId(), queryVectorString);
        } else {
            relevantChunks = chunkRepository.findTopChunksByFolderAndVector(chatRequest.getFolderId(), queryVectorString);
        }

        StringBuilder contextBuilder = new StringBuilder();
        java.util.Set<UUID> referencedDocIds = new java.util.HashSet<>();

        for (com.tugnw.aistudy.domain.entity.DocumentChunk chunk : relevantChunks) {
            contextBuilder.append("--- Đoạn trích từ Tài liệu ID ").append(chunk.getDocumentId()).append(" ---\n");
            contextBuilder.append(chunk.getContent()).append("\n\n");
            referencedDocIds.add((UUID) (Object) chunk.getDocumentId());
        }

        String prompt = "Bạn là một trợ lý học tập AI thông minh, đóng vai trò là một giáo viên tài năng của hệ thống AI Study Hub.\n"
                + "Nhiệm vụ của bạn là trả lời câu hỏi của học sinh dựa trên các tài liệu học tập được cung cấp dưới đây.\n\n"
                + "--- TÀI LIỆU THAM KHẢO ---\n"
                + contextBuilder.toString()
                + "--- CÂU HỎI CỦA HỌC SINH ---\n"
                + chatRequest.getQuestion() + "\n\n"
                + "YÊU CẦU TRẢ LỜI:\n"
                + "1. Chỉ trả lời dựa trên thông tin có trong phần 'TÀI LIỆU THAM KHẢO'. Không tự bịa đặt thông tin nằm ngoài tài liệu.\n"
                + "2. Trả lời một cách mạch lạc, rõ ràng, có phân tích cấu trúc, định dạng Markdown (bôi đậm, gạch đầu dòng) để học sinh dễ tiếp thu.\n"
                + "3. Nếu tài liệu tham khảo không chứa câu trả lời, hãy lịch sự đáp: 'Xin lỗi, kiến thức này nằm ngoài các tài liệu hiện có trong thư mục học tập của bạn.'";

        String aiAnswer = generateTextFromGemini(prompt);
        return new RagChatResponse(aiAnswer, referencedDocIds);
    }

    @Override
    public String generateContent(String prompt) {
        System.out.println("[LOG - RAG] Generating text from prompt using Gemini.");
        return generateTextFromGemini(prompt);
    }

    private String generateTextFromGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + chatModel + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> parts = new HashMap<>();
        parts.put("parts", List.of(textPart));

        Map<String, Object> contents = new HashMap<>();
        contents.put("contents", List.of(parts));

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
            System.err.println("Lỗi nghiêm trọng khi gọi Gemini Chat API: " + e.getMessage());
        }
        return "Hệ thống AI gặp sự cố khi xử lý câu hỏi của bạn. Vui lòng thử lại sau.";
    }
}