package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.service.AdminDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Document Management", description = "Admin endpoints for managing documents")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;

    @GetMapping
    @Operation(summary = "List all documents", description = "Get list of all documents in system")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getAllDocuments() {
        return ResponseEntity.ok(ApiResponse.success("Documents retrieved", adminDocumentService.getAllDocuments()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document", description = "Soft delete any document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        adminDocumentService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore document", description = "Restore a soft-deleted document")
    public ResponseEntity<ApiResponse<Void>> restoreDocument(@PathVariable UUID id) {
        adminDocumentService.restoreDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document restored", null));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve document", description = "Approve a pending document for publishing")
    public ResponseEntity<ApiResponse<Void>> approveDocument(@PathVariable UUID id) {
        adminDocumentService.approveDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document approved", null));
    }

    @GetMapping("/trash")
    @Operation(summary = "List trash documents", description = "Get list of soft-deleted documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getTrashDocuments() {
        return ResponseEntity.ok(ApiResponse.success("Trash documents retrieved", adminDocumentService.getTrashDocuments()));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject document", description = "Reject a pending document")
    public ResponseEntity<ApiResponse<Void>> rejectDocument(@PathVariable UUID id) {
        adminDocumentService.rejectDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document rejected", null));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filter by status", description = "Get documents by approval status")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(ApiResponse.success("Documents by status retrieved", adminDocumentService.getDocumentsByStatus(status)));
    }
}