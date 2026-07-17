package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/process/{documentId}")
    public ResponseEntity<String> processDocumentPipeline(
            @PathVariable UUID documentId,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        log.info("[CTRL] POST /rag/process documentId={} ownerId={} thread={}",
                documentId, ownerId, Thread.currentThread().getName());
        log.info("[CTRL] Calling processAndSaveDocumentPipeline...");
        try {
            ragService.processAndSaveDocumentPipeline(documentId, ownerId);
            log.info("[CTRL] processAndSaveDocumentPipeline returned normally documentId={}", documentId);
            return ResponseEntity.ok("Xử lý tài liệu và nạp cơ sở dữ liệu Vector RAG thành công!");
        } catch (Exception e) {
            log.error("[CTRL] processAndSaveDocumentPipeline threw documentId={} class={} message={}",
                    documentId, e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.internalServerError().body("Pipeline thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/status/{documentId}")
    public ResponseEntity<Map<String, String>> getDocumentStatus(
            @PathVariable UUID documentId,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        String status = ragService.getDocumentProcessingStatus(documentId, ownerId);
        Map<String, String> response = Map.of("documentId", documentId.toString(), "status", status);
        log.info("[STATUS] GET /rag/status documentId={} response={}", documentId, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<RagChatResponse> chatWithFolder(
            @RequestBody com.tugnw.aistudy.domain.dto.rag.RagChatRequest request,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        RagChatResponse response = ragService.chatWithFolderContext(request, ownerId);
        return ResponseEntity.ok(response);
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("User chưa đăng nhập");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }

        throw new RuntimeException("Không thể xác định user");
    }
}
