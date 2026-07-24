package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
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

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @PostMapping("/process/{documentId}")
    public ApiResponse<String> processDocumentPipeline(
            @PathVariable UUID documentId, Authentication authentication) {
        var uid = userId(authentication);
        try {
            ragService.processAndSaveDocumentPipeline(documentId, uid);
            return ApiResponse.success("Xử lý tài liệu và nạp cơ sở dữ liệu Vector RAG thành công!");
        } catch (Exception e) {
            return ApiResponse.error("Pipeline thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/status/{documentId}")
    public ApiResponse<Map<String, String>> getDocumentStatus(
            @PathVariable UUID documentId, Authentication authentication) {
        String status = ragService.getDocumentProcessingStatus(documentId, userId(authentication));
        Map<String, String> response = Map.of("documentId", documentId.toString(), "status", status);
        return ApiResponse.success(response);
    }

    @PostMapping("/chat")
    public ApiResponse<RagChatResponse> chatWithFolder(
            @RequestBody com.tugnw.aistudy.domain.dto.rag.RagChatRequest request,
            Authentication authentication) {
        RagChatResponse response = ragService.chatWithFolderContext(request, userId(authentication));
        return ApiResponse.success(response);
    }

}
