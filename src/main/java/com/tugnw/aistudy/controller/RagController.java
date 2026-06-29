package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/process/{documentId}")
    public ResponseEntity<String> processDocumentPipeline(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Account account) {
        try {
            ragService.processAndSaveDocumentPipeline(documentId, account.getId());
            return ResponseEntity.ok("Xử lý tài liệu và nạp cơ sở dữ liệu Vector RAG thành công!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Pipeline thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/process-folder/{folderId}")
    public ResponseEntity<String> processFolderPipeline(
            @PathVariable UUID folderId,
            @AuthenticationPrincipal Account account) {
        try {
            ragService.processFolderPipeline(folderId, account.getId());
            return ResponseEntity.ok("Xử lý toàn bộ tài liệu trong thư mục thành công!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Pipeline folder thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/status/{documentId}")
    public ResponseEntity<Map<String, String>> getDocumentStatus(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Account account) {
        String status = ragService.getDocumentProcessingStatus(documentId, account.getId());
        return ResponseEntity.ok(Map.of("documentId", documentId.toString(), "status", status));
    }

    @PostMapping("/chat")
    public ResponseEntity<com.tugnw.aistudy.domain.dto.rag.RagChatResponse> chatWithFolder(
            @RequestBody com.tugnw.aistudy.domain.dto.rag.RagChatRequest request,
            @AuthenticationPrincipal Account account) {
        try {
            com.tugnw.aistudy.domain.dto.rag.RagChatResponse response = ragService.chatWithFolderContext(request, account.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                new com.tugnw.aistudy.domain.dto.rag.RagChatResponse("Lỗi hệ thống chat: " + e.getMessage(), java.util.Collections.emptySet()));
        }
    }
}