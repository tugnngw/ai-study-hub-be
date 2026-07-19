package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.AdminDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
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

    @GetMapping("/trash")
    @Operation(summary = "Get trash documents", description = "Get list of soft-deleted documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getTrashDocuments() {
        return ResponseEntity.ok(ApiResponse.success("Trash documents retrieved", adminDocumentService.getTrashDocuments()));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve document")
    public ResponseEntity<ApiResponse<Void>> approveDocument(@PathVariable String id) {
        log.info("=== [APPROVE CONTROLLER] id={} ===", id);
        adminDocumentService.approveDocument(UUID.fromString(id));
        log.info("=== [APPROVE CONTROLLER] success, returning 200 ===");
        return ResponseEntity.ok(ApiResponse.success("Document approved", null));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject document")
    public ResponseEntity<ApiResponse<Void>> rejectDocument(@PathVariable String id, @RequestBody(required = false) String reason) {
        String cleanReason = (reason == null || reason.equals("undefined")) ? "" : reason;
        adminDocumentService.rejectDocument(UUID.fromString(id), cleanReason);
        return ResponseEntity.ok(ApiResponse.success("Document rejected", null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document - DISABLED", description = "Admin cannot delete user documents")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable String id) {
        adminDocumentService.deleteDocument(UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore document from trash")
    public ResponseEntity<ApiResponse<Void>> restoreDocument(@PathVariable String id, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID adminId = userDetails.getAccount().getId();
        String adminName = userDetails.getAccount().getFullName();
        adminDocumentService.restoreDocument(UUID.fromString(id), adminId, adminName);
        return ResponseEntity.ok(ApiResponse.success("Document restored successfully", null));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Filter by status", description = "Get documents by approval status")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsByStatus(@PathVariable String status) {
        List<DocumentResponse> docs = adminDocumentService.getDocumentsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Documents by status retrieved", docs));
    }
}