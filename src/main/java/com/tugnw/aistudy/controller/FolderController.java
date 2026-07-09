package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.FolderService;
import com.tugnw.aistudy.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Folders", description = "Folder management (academic hierarchy)")
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RestController
@RequestMapping("/api/folder")
@Validated
public class FolderController {

    private final FolderService folderService;
    private final ShareService shareService;

    public FolderController(FolderService folderService, ShareService shareService) {
        this.folderService = folderService;
        this.shareService = shareService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a folder under a subject")
    public ResponseEntity<FolderResponse> createFolder(
            @RequestBody @Valid FolderCreateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.createFolder(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/getall")
    @Operation(summary = "Get all folders for current user")
    public ResponseEntity<List<FolderResponse>> getFolders(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        List<FolderResponse> responses = folderService.getFoldersByOwner(ownerId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/getbyid/{id}")
    @Operation(summary = "Get folder by ID")
    public ResponseEntity<FolderResponse> getFolderById(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.getFolderById(id, ownerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "Update folder")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable UUID id,
            @RequestBody FolderUpdateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.updateFolder(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Soft-delete folder")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        folderService.deleteFolder(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a folder with another user")
    public ResponseEntity<ShareResponse> shareFolder(
            @PathVariable UUID id,
            @RequestBody ShareRequest request,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        request.setFolderId(id);
        return ResponseEntity.ok(shareService.shareFolder(request, ownerId));
    }

    @GetMapping("/{id}/share-info")
    @Operation(summary = "Get folder share info")
    public ResponseEntity<ShareResponse> getFolderShareInfo(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(shareService.getShareInfo(id, "folder", ownerId));
    }

    private UUID getCurrentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }
        throw new RuntimeException("User no login");
    }
}
