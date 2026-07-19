package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")   // Giống test case Folder, dễ test
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload new document
     */
    @PostMapping(consumes = {"multipart/form-data"})
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Upload a document",
            description = "Upload a document file with metadata. Supported file types: PDF, DOCX, TXT, PPTX. Max size: 50MB"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Document upload request with file and metadata",
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "multipart/form-data",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentUploadRequest.class)
            )
    )
    public ResponseEntity<DocumentResponse> uploadDocument(
            @ModelAttribute @Valid DocumentUploadRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.uploadDocument(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all documents of current user
     */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        List<DocumentResponse> responses = documentService.getDocumentsByOwner(ownerId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get documents by folder
     */
    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<DocumentResponse>> getDocumentsByFolder(
            @PathVariable UUID folderId,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        List<DocumentResponse> responses = documentService.getDocumentsByFolder(ownerId, folderId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get document detail by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable Long id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.getDocumentById(id, ownerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Update document metadata / move folder
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @RequestBody DocumentUpdateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.updateDocument(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete document (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        documentService.deleteDocument(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get download URL
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<String> getDownloadUrl(
            @PathVariable Long id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        String url = documentService.getDocumentDownloadUrl(id, ownerId);
        return ResponseEntity.ok(url);
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("User chưa đăng nhập");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();   // Giả sử bạn đã có getId()
        }

        throw new RuntimeException("Không thể xác định user");
    }
}