package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.rag.RagProcessRequest;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/process")
    public ResponseEntity<String> processDocumentPipeline(@RequestBody RagProcessRequest request) {
        try {
            // Chạy toàn bộ luồng: Tải -> Trích xuất -> Chunking -> Embedding -> Lưu DB Vector
            ragService.processAndSaveDocumentPipeline(request.getDocumentId());
            return ResponseEntity.ok("Xử lý tài liệu và nạp cơ sở dữ liệu Vector RAG thành công!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Pipeline thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<com.tugnw.aistudy.domain.dto.rag.RagChatResponse> chatWithFolder(@RequestBody com.tugnw.aistudy.domain.dto.rag.RagChatRequest request) {
        try {
            com.tugnw.aistudy.domain.dto.rag.RagChatResponse response = ragService.chatWithFolderContext(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new com.tugnw.aistudy.domain.dto.rag.RagChatResponse("Lỗi hệ thống chat: " + e.getMessage(), java.util.Collections.emptySet()));
        }
    }
}