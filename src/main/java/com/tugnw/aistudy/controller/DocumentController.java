package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.CurrentUserService;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final CurrentUserService currentUserService;

    @PostMapping(consumes = {"multipart/form-data"})
    @Operation(summary = "Upload documents to a folder")
    public ApiResponse<List<DocumentResponse>> uploadDocument(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "folderId", required = false) UUID folderId) {

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setFiles(files);
        if (title != null) request.setTitle(title);
        if (description != null) request.setDescription(description);
        request.setFolderId(folderId);
        List<DocumentResponse> responses = documentService.uploadDocuments(currentUserService.getCurrentUserId(), request);
        return ApiResponse.success(responses);
    }

    @GetMapping
    @Operation(summary = "List current user's documents")
    public ApiResponse<List<DocumentResponse>> getDocuments() {
        return ApiResponse.success(documentService.getDocumentsByOwner(currentUserService.getCurrentUserId()));
    }

    @GetMapping("/folder/{folderId}")
    @Operation(summary = "List documents in a folder")
    public ApiResponse<List<DocumentResponse>> getDocumentsByFolder(@PathVariable UUID folderId) {
        List<DocumentResponse> responses = documentService.getDocumentsByFolder(currentUserService.getCurrentUserId(), folderId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/shared/folder/{folderId}")
    @Operation(summary = "List documents in a shared folder")
    public ApiResponse<List<DocumentResponse>> getSharedFolderDocuments(@PathVariable UUID folderId) {
        List<DocumentResponse> responses = documentService.getSharedFolderDocuments(currentUserService.getCurrentUserId(), folderId);
        return ApiResponse.success(responses);
    }

    @GetMapping("/shared")
    @Operation(summary = "List shared documents (TODO)")
    public ApiResponse<List<DocumentResponse>> getSharedDocuments() {
        return ApiResponse.success(List.of());

    }

    @GetMapping("/trash")
    @Operation(summary = "List soft-deleted documents")
    public ApiResponse<List<DocumentResponse>> getTrashDocuments() {
        return ApiResponse.success(documentService.getTrashDocuments(currentUserService.getCurrentUserId()));

    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore document from trash")
    public ApiResponse<Void> restoreDocument(@PathVariable UUID id) {
        documentService.restoreDocument(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public ApiResponse<DocumentResponse> getDocumentById(@PathVariable UUID id) {
        return ApiResponse.success(documentService.getDocumentById(id, currentUserService.getCurrentUserId()));
    }

    @GetMapping("/shared/{id}")
    @Operation(summary = "Get shared document by ID")
    public ApiResponse<DocumentResponse> getSharedDocumentById(@PathVariable UUID id) {
        return ApiResponse.success(documentService.getSharedDocumentById(id, currentUserService.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update document metadata")
    public ApiResponse<DocumentResponse> updateDocument(
            @PathVariable UUID id,
            @RequestBody DocumentUpdateRequest request) {
        return ApiResponse.success(documentService.updateDocument(id, currentUserService.getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete document")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete document (from trash)")
    public ApiResponse<Void> permanentDeleteDocument(@PathVariable UUID id) {
        documentService.permanentDeleteDocument(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Get document download URL")
    public ApiResponse<String> getDownloadUrl(
            @PathVariable UUID id) {
        return ApiResponse.success(documentService.getDocumentDownloadUrl(id, currentUserService.getCurrentUserId()));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a document with another user")
    public ApiResponse<ShareResponse> shareDocument(
            @PathVariable UUID id,
            @RequestBody ShareRequest request) {
        request.setDocumentId(id);
        return ApiResponse.success(shareService.shareDocument(request, currentUserService.getCurrentUserId()));
    }

    @GetMapping("/{id}/share-info")
    @Operation(summary = "Get document share info")
    public ApiResponse<ShareResponse> getDocumentShareInfo(@PathVariable UUID id) {
        return ApiResponse.success(shareService.getShareInfo(id, "document", currentUserService.getCurrentUserId()));
    }

}
