package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.chat.ChatMessageResponse;
import com.tugnw.aistudy.domain.dto.chat.ChatSessionResponse;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.entity.ChatMessage;
import com.tugnw.aistudy.domain.entity.ChatSession;
import com.tugnw.aistudy.repository.ChatMessageRepository;
import com.tugnw.aistudy.repository.ChatSessionRepository;
import com.tugnw.aistudy.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Chat Sessions", description = "RAG chat history management")
@RestController
@RequestMapping("/api/v1/rag/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @GetMapping
    @Operation(summary = "List chat sessions for a document (newest first)")
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @RequestParam UUID documentId,
            Authentication authentication) {

        List<ChatSession> sessions = chatSessionRepository
                .findByAccountIdAndDocumentIdOrderByUpdatedAtDesc(userId(authentication), documentId);

        List<ChatSessionResponse> responses = sessions.stream()
                .map(s -> {
                    int msgCount = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId()).size();
                    return ChatSessionResponse.builder()
                            .id(s.getId())
                            .documentId(s.getDocumentId())
                            .title(s.getTitle())
                            .messageCount(msgCount)
                            .createdAt(s.getCreatedAt())
                            .updatedAt(s.getUpdatedAt())
                            .build();
                })
                .toList();

        return ApiResponse.success(responses);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session with all messages")
    public ApiResponse<ChatSessionResponse> getSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getAccountId().equals(userId(authentication))) {
            throw new AccessDeniedException("No permission");
        }

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessageResponse> msgResponses = messages.stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getId())
                        .senderType(m.getSenderType())
                        .content(m.getContent())
                        .referencedChunks(m.getReferencedChunks())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return ApiResponse.success(ChatSessionResponse.builder()
                .id(session.getId())
                .documentId(session.getDocumentId())
                .title(session.getTitle())
                .messageCount(msgResponses.size())
                .messages(msgResponses)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build());
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete a chat session")
    public ApiResponse<Void> deleteSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getAccountId().equals(userId(authentication))) {
            throw new AccessDeniedException("No permission");
        }

        chatSessionRepository.delete(session);
        return ApiResponse.success(null);
    }
}
