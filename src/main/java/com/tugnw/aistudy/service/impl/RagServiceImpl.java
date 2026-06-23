package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.rag.RagChatRequest;
import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.DocumentChunkRepository;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    
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

    // --- THÔNG SỐ CẤU HÌNH RECURSIVE CHUNKING ---
    private static final int MAX_CHUNK_SIZE = 1000; // Số ký tự tối đa 1 chunk
    private static final int CHUNK_OVERLAP = 200;  // Số ký tự gối đầu giữa các chunk

    // ==========================================
    // GIAI ĐOẠN 1: TẢI FILE & TRÍCH XUẤT VĂN BẢN
    // ==========================================
    @Override
    public String extractTextFromDocument(Long documentId) throws Exception {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new RuntimeException("Tài liệu không tồn tại hoặc đã bị xóa."));

        String fileUrl = document.getCloudinaryUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new RuntimeException("Tài liệu chưa có URL lưu trữ trên Cloudinary.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Lỗi khi tải file từ Cloudinary. Mã trạng thái: " + response.statusCode());
        }

        try (InputStream inputStream = response.body()) {
            return tika.parseToString(inputStream);
        }
    }

    // ==========================================
    // GIAI ĐOẠN 2: CHUNKING & EMBEDDING PIPELINE
    // ==========================================
    @Override
    public void processAndSaveDocumentPipeline(Long documentId) throws Exception {
        // 1. Lấy dữ liệu chữ thô bằng Apache Tika
        String rawText = extractTextFromDocument(documentId);
        if (rawText == null || rawText.isBlank()) {
            throw new RuntimeException("Không thể bóc tách nội dung văn bản từ tài liệu này.");
        }

        // 2. Thực hiện băm đệ quy (Recursive Character Chunking)
        List<String> textChunks = recursiveChunking(rawText);

        // 3. Vòng lặp xử lý nhúng và lưu trữ cho từng mảnh chunk
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i);
            
            // Gọi Gemini API lấy mảng số thực
            List<Double> vector = getEmbeddingFromGemini(chunkContent);

            if (!vector.isEmpty()) {
                // Biến List<Double> thành dạng chuỗi "[0.123, -0.456, ...]" để Native Query xử lý
                String vectorString = vector.toString();
                chunkRepository.saveChunkWithVector(documentId, i, chunkContent, vectorString);
            } else {
                throw new RuntimeException("Thất bại trong việc tạo vector ở chunk thứ: " + i);
            }
        }

        // 4. Cập nhật trạng thái tài liệu sang ACTIVE sau khi hoàn thành RAG pipeline
        Document document = documentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (document != null) {
            document.setStatus("active");
            documentRepository.save(document);
        }
    }

    // Hàm nội bộ gọi API Gemini Studio
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

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> embeddingResult = (Map<String, Object>) response.getBody().get("embedding");
                return (List<Double>) embeddingResult.get("values");
            }
        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng khi gọi Gemini Embedding API: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // Logic điều phối phân tách chuỗi đệ quy dựa trên mức độ ưu tiên của dấu phân tách
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
            // Trường hợp bất khả kháng, cắt cứng chuỗi văn bản theo độ dài tối đa
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
                    // Mẩu text đơn lẻ vẫn quá lớn, hạ cấp xuống dấu phân tách nhỏ hơn
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
    public RagChatResponse chatWithFolderContext(RagChatRequest chatRequest) throws Exception {
        // 1. Biến câu hỏi của User thành mảng Vector bằng gemini-embedding-2
        List<Double> queryVector = getEmbeddingFromGemini(chatRequest.getQuestion());
        if (queryVector.isEmpty()) {
            throw new RuntimeException("Không thể tạo vector cho câu hỏi.");
        }

        // 2. Thực hiện Vector Search dùng lệnh JOIN để quét Top 5 chunk trong thư mục đó
        List<com.tugnw.aistudy.domain.entity.DocumentChunk> relevantChunks = 
                chunkRepository.findTopChunksByFolderAndVector(chatRequest.getFolderId(), queryVector.toString());

        // 3. Tổng hợp nội dung các đoạn văn tìm được làm ngữ cảnh (Context)
        StringBuilder contextBuilder = new StringBuilder();
        java.util.Set<Long> referencedDocIds = new java.util.HashSet<>();
        
        for (com.tugnw.aistudy.domain.entity.DocumentChunk chunk : relevantChunks) {
            contextBuilder.append("--- Đoạn trích từ Tài liệu ID ").append(chunk.getDocumentId()).append(" ---\n");
            contextBuilder.append(chunk.getContent()).append("\n\n");
            referencedDocIds.add(chunk.getDocumentId());
        }

        // 4. Xây dựng Prompt tối ưu (System Instruction ngầm) ép AI làm giáo viên
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

        // 5. Gửi Prompt tổng hợp cho siêu model gemini-2.5-flash để lấy câu trả lời
        String aiAnswer = generateTextFromGemini(prompt);

        // 6. Trả về kết quả hoàn chỉnh cho Frontend
        return new RagChatResponse(aiAnswer, referencedDocIds);
    }

    // Hàm nội bộ gọi API tạo sinh văn bản (Text Generation) của Gemini Studio
    private String generateTextFromGemini(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + chatModel + ":generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Cấu trúc payload chuẩn của Google AI Studio dành cho dòng Gemini 1.5/2.5
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
                // Bóc tách JSON lồng nhau từ phản hồi của Google để lấy raw text câu trả lời
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