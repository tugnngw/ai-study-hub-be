package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.rag.RagChatResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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
        try {
            ragService.processAndSaveDocumentPipeline(documentId, ownerId);
            return ResponseEntity.ok("Xử lý tài liệu và nạp cơ sở dữ liệu Vector RAG thành công!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Pipeline thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/status/{documentId}")
    public ResponseEntity<Map<String, String>> getDocumentStatus(
            @PathVariable UUID documentId,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        String status = ragService.getDocumentProcessingStatus(documentId, ownerId);
        return ResponseEntity.ok(Map.of("documentId", documentId.toString(), "status", status));
    }

    @PostMapping("/chat")
    public ResponseEntity<RagChatResponse> chatWithFolder(
            @RequestBody com.tugnw.aistudy.domain.dto.rag.RagChatRequest request,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        try {
            RagChatResponse response = ragService.chatWithFolderContext(request, ownerId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    com.tugnw.aistudy.domain.dto.rag.RagChatResponse.builder()
                            .sessionId(null)
                            .answer("Lỗi hệ thống chat: " + e.getMessage())
                            .referencedDocumentIds(java.util.Collections.emptySet())
                            .build()
            );
        }
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
