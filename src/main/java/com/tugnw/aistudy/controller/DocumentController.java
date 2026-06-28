package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")   // Giống test case Folder, dễ test
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;

    public DocumentController(DocumentService documentService, ShareService shareService) {
        this.documentService = documentService;
        this.shareService = shareService;
    }

    /**
     * Upload new document
     */
    @PostMapping(consumes = {"multipart/form-data"})
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Upload one or more documents",
            description = "Upload document files with metadata. Supported file types: PDF, DOCX, TXT, PPTX. Max size: 50MB each. You can select multiple files at once."
    )
    public ResponseEntity<List<DocumentResponse>> uploadDocument(
            @Parameter(description = "Files to upload (multiple files allowed)", required = true, 
                      content = @Content(mediaType = "multipart/form-data", 
                      array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))))
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Title for all files")
            @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "Description")
            @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "Folder ID")
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @Parameter(description = "Subject ID")
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            Authentication authentication) {

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setFiles(files);
        if (title != null) request.setTitle(title);
        if (description != null) request.setDescription(description);
        request.setFolderId(folderId);
        request.setSubjectId(subjectId);

        UUID ownerId = getCurrentUserId(authentication);
        List<DocumentResponse> responses = documentService.uploadDocuments(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
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
     * Get shared documents with current user
     */
    @GetMapping("/shared")
    public ResponseEntity<List<DocumentResponse>> getSharedDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        // TODO: Implement share logic
        return ResponseEntity.ok(List.of());
    }

    /**
     * Get soft-deleted documents (trash)
     */
    @GetMapping("/trash")
    public ResponseEntity<List<DocumentResponse>> getTrashDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        // TODO: Implement trash logic
        return ResponseEntity.ok(List.of());
    }

    /**
     * Restore document from trash
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreDocument(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        // TODO: Implement restore logic
        return ResponseEntity.ok().build();
    }

    /**
     * Get document detail by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable UUID id,
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
            @PathVariable UUID id,
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
            @PathVariable UUID id,
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
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        String url = documentService.getDocumentDownloadUrl(id, ownerId);
        return ResponseEntity.ok(url);
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ShareResponse> shareDocument(
            @PathVariable UUID id,
            @RequestBody ShareRequest request,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        request.setDocumentId(id);
        return ResponseEntity.ok(shareService.shareDocument(request, ownerId));
    }

    @GetMapping("/{id}/share-info")
    public ResponseEntity<ShareResponse> getDocumentShareInfo(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(shareService.getShareInfo(id, "document", ownerId));
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