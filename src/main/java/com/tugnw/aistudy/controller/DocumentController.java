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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;

    public DocumentController(DocumentService documentService, ShareService shareService) {
        this.documentService = documentService;
        this.shareService = shareService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<List<DocumentResponse>> uploadDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "folderId", required = false) UUID folderId,
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

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(documentService.getDocumentsByOwner(ownerId));
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<DocumentResponse>> getDocumentsByFolder(
            @PathVariable UUID folderId,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        List<DocumentResponse> responses = documentService.getDocumentsByFolder(ownerId, folderId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/shared/folder/{folderId}")
    public ResponseEntity<List<DocumentResponse>> getSharedFolderDocuments(
            @PathVariable UUID folderId,
            Authentication authentication) {

        UUID userId = getCurrentUserId(authentication);
        List<DocumentResponse> responses = documentService.getSharedFolderDocuments(userId, folderId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/shared")
    public ResponseEntity<List<DocumentResponse>> getSharedDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(List.of()); // TODO: Implement share logic
    }

    @GetMapping("/trash")
    public ResponseEntity<List<DocumentResponse>> getTrashDocuments(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(documentService.getTrashDocuments(ownerId));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreDocument(@PathVariable UUID id, Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        documentService.restoreDocument(id, ownerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.getDocumentById(id, ownerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared/{id}")
    public ResponseEntity<DocumentResponse> getSharedDocumentById(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID requesterId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.getSharedDocumentById(id, requesterId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable UUID id,
            @RequestBody DocumentUpdateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        DocumentResponse response = documentService.updateDocument(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        documentService.deleteDocument(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<String> getDownloadUrl(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(documentService.getDocumentDownloadUrl(id, ownerId));
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
            return userDetails.getAccount().getId();
        }

        throw new RuntimeException("Không thể xác định user");
    }
}