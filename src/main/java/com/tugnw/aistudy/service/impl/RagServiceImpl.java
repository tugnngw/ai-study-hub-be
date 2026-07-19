package com.tugnw.aistudy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tugnw.aistudy.service.RagService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class RagServiceImpl implements RagService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    //UPLOAD & EMBEDDING
    @Override
    public String processAndSaveDocument(MultipartFile file, Long documentId) throws Exception {
        
        File tempFile = File.createTempFile("upload-", ".pdf");
        file.transferTo(tempFile);
        
        
        String text;
        try (PDDocument document = Loader.loadPDF(tempFile)) {
            text = new PDFTextStripper().getText(document);
        }
        tempFile.delete(); // Xóa file tạm ngay lập tức

        
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += 500) {
            chunks.add(text.substring(i, Math.min(i + 500, text.length())));
        }

        
        int index = 0;
        for (String chunk : chunks) {
            String vectorString = getEmbeddingFromGemini(chunk);
            if (vectorString != null) {
                String sql = "INSERT INTO document_chunk (document_id, chunk_index, content, embedding_vector) VALUES (?, ?, ?, CAST(? AS vector))";
                jdbcTemplate.update(sql, documentId, index++, chunk, vectorString);
            }
        }
        return "Thành công! Đã băm nhỏ và lưu " + chunks.size() + " vector vào Database.";
    }

    //CHAT
    @Override
    public String askQuestion(Long documentId, String question) throws Exception {
        String questionVector = getEmbeddingFromGemini(question);

        String sql = "SELECT content FROM document_chunk WHERE document_id = ? ORDER BY embedding_vector <=> CAST(? AS vector) LIMIT 3";
        List<String> relevantChunks = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("content"), documentId, questionVector);

        String context = String.join("\n...\n", relevantChunks);
        return getAnswerFromGemini(context, question);
    }

    //API
    private String getEmbeddingFromGemini(String text) throws Exception {
        String url = "[https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=](https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=)" + geminiApiKey;
        String safeText = text.replace("\"", "\\\"").replace("\n", " ");
        String requestBody = "{\"model\": \"models/text-embedding-004\", \"content\": {\"parts\": [{\"text\": \"" + safeText + "\"}]}}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);

        JsonNode values = objectMapper.readTree(response.getBody()).path("embedding").path("values");
        List<String> vectorList = new ArrayList<>();
        for (JsonNode node : values) { vectorList.add(node.asText()); }
        return "[" + String.join(",", vectorList) + "]";
    }

    private String getAnswerFromGemini(String context, String question) throws Exception {
        String url = "[https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=](https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=)" + geminiApiKey;
        String safeContext = context.replace("\"", "\\\"").replace("\n", " ");
        String safeQuestion = question.replace("\"", "\\\"").replace("\n", " ");
        
        String prompt = "Dựa VÀO ĐÚNG ngữ cảnh sau đây, hãy trả lời câu hỏi. \n\nNgữ cảnh tài liệu:\n" + safeContext + "\n\nCâu hỏi:\n" + safeQuestion;
        String requestBody = "{\"contents\": [{\"parts\": [{\"text\": \"" + prompt + "\"}]}]}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);

        return objectMapper.readTree(response.getBody()).path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
    }
}
