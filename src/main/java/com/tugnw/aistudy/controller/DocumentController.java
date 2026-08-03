package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.UploadConfigResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document CRUD, upload, share, trash")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @GetMapping("/upload-config")
    @Operation(summary = "Get upload constraints (allowed extensions, max size)")
    public ApiResponse<UploadConfigResponse> getUploadConfig() {
        return ApiResponse.success(UploadConfigResponse.builder()
                .allowedExtensions(List.of(".pdf", ".txt"))
                .maxFileSize(50L * 1024 * 1024)
                .build());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @Operation(summary = "Upload documents to a folder")
    public ApiResponse<List<DocumentResponse>> uploadDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            Authentication authentication) {

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setFiles(files);
        if (title != null) request.setTitle(title);
        if (description != null) request.setDescription(description);
        request.setFolderId(folderId);
        List<DocumentResponse> responses = documentService.uploadDocuments(userId(authentication), request);
        return ApiResponse.success(responses);
    }

    @GetMapping
    @Operation(summary = "List current user's documents")
    public ApiResponse<List<DocumentResponse>> getDocuments(Authentication authentication) {
        return ApiResponse.success(documentService.getDocumentsByOwner(userId(authentication)));
    }

    @GetMapping("/folder/{folderId}")
    @Operation(summary = "List documents in a folder")
    public ApiResponse<List<DocumentResponse>> getDocumentsByFolder(
            @PathVariable UUID folderId, Authentication authentication) {
        List<DocumentResponse> responses = documentService.getDocumentsByFolder(userId(authentication), folderId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/shared/folder/{folderId}")
    @Operation(summary = "List documents in a shared folder")
    public ApiResponse<List<DocumentResponse>> getSharedFolderDocuments(
            @PathVariable UUID folderId, Authentication authentication) {
        List<DocumentResponse> responses = documentService.getSharedFolderDocuments(userId(authentication), folderId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/shared")
    @Operation(summary = "List shared documents (TODO)")
    public ApiResponse<List<DocumentResponse>> getSharedDocuments() {
        return ApiResponse.success(List.of());
    }

    @GetMapping("/trash")
    @Operation(summary = "List soft-deleted documents")
    public ApiResponse<List<DocumentResponse>> getTrashDocuments(Authentication authentication) {
        return ApiResponse.success(documentService.getTrashDocuments(userId(authentication)));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore document from trash")
    public ApiResponse<Void> restoreDocument(@PathVariable UUID id, Authentication authentication) {
        documentService.restoreDocument(id, userId(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public ApiResponse<DocumentResponse> getDocumentById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(documentService.getDocumentById(id, userId(authentication)));
    }

    @GetMapping("/shared/{id}")
    @Operation(summary = "Get shared document by ID")
    public ApiResponse<DocumentResponse> getSharedDocumentById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(documentService.getSharedDocumentById(id, userId(authentication)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update document metadata")
    public ApiResponse<DocumentResponse> updateDocument(
            @PathVariable UUID id, @RequestBody DocumentUpdateRequest request, Authentication authentication) {
        return ApiResponse.success(documentService.updateDocument(id, userId(authentication), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete document")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id, Authentication authentication) {
        documentService.deleteDocument(id, userId(authentication));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete document (from trash)")
    public ApiResponse<Void> permanentDeleteDocument(@PathVariable UUID id, Authentication authentication) {
        documentService.permanentDeleteDocument(id, userId(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Get document download URL")
    public ApiResponse<String> getDownloadUrl(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(documentService.getDocumentDownloadUrl(id, userId(authentication)));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a document with another user")
    public ApiResponse<ShareResponse> shareDocument(
            @PathVariable UUID id, @RequestBody ShareRequest request, Authentication authentication) {
        request.setDocumentId(id);
        return ApiResponse.success(shareService.shareDocument(request, userId(authentication)));
    }

    @GetMapping("/{id}/share-info")
    @Operation(summary = "Get document share info")
    public ApiResponse<ShareResponse> getDocumentShareInfo(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(shareService.getShareInfo(id, "document", userId(authentication)));
    }

}
