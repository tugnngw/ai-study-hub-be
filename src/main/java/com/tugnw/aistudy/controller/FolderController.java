package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.CurrentUserService;
import com.tugnw.aistudy.service.FolderService;
import com.tugnw.aistudy.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Folders", description = "Folder management (academic hierarchy)")
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RestController
@RequestMapping("/api/folder")
@Validated
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final ShareService shareService;
    private final CurrentUserService currentUserService;

    @PostMapping("/create")
    @Operation(summary = "Create a folder under a subject")
    public ApiResponse<FolderResponse> createFolder(
            @RequestBody @Valid FolderCreateRequest request) {

        FolderResponse response = folderService.createFolder(currentUserService.getCurrentUserId(), request);
        return ApiResponse.success("Folder created successfully", response);
    }

    @GetMapping("/getall")
    @Operation(summary = "Get all folders for current user")
    public ApiResponse<List<FolderResponse>> getFolders() {
        List<FolderResponse> responses = folderService.getFoldersByOwner(currentUserService.getCurrentUserId());
        return ApiResponse.success(responses);
    }

    @GetMapping("/getbyid/{id}")
    @Operation(summary = "Get folder by ID")
    public ApiResponse<FolderResponse> getFolderById(@PathVariable UUID id) {
        FolderResponse response = folderService.getFolderById(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(response);
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "Update folder")
    public ApiResponse<FolderResponse> updateFolder(
            @PathVariable UUID id,
            @RequestBody FolderUpdateRequest request) {
        FolderResponse response = folderService.updateFolder(id, currentUserService.getCurrentUserId(), request);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Soft-delete folder")
    public ApiResponse<Void> deleteFolder(
            @PathVariable UUID id) {
        folderService.deleteFolder(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @GetMapping("/trash")
    @Operation(summary = "List soft-deleted folders")
    public ApiResponse<List<FolderResponse>> getTrashFolders() {
        return ApiResponse.success(folderService.getTrashFolders(currentUserService.getCurrentUserId()));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore folder from trash")
    public ApiResponse<Void> restoreFolder(@PathVariable UUID id) {
        folderService.restoreFolder(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete folder (from trash)")
    public ApiResponse<Void> permanentDeleteFolder(@PathVariable UUID id) {
        folderService.permanentDeleteFolder(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a folder with another user")
    public ApiResponse<ShareResponse> shareFolder(
            @PathVariable UUID id,
            @RequestBody ShareRequest request) {
        request.setFolderId(id);
        return ApiResponse.success(shareService.shareFolder(request, currentUserService.getCurrentUserId()));
    }

    @GetMapping("/{id}/share-info")
    @Operation(summary = "Get folder share info")
    public ApiResponse<ShareResponse> getFolderShareInfo(
            @PathVariable UUID id) {
        return ApiResponse.success(shareService.getShareInfo(id, "folder", currentUserService.getCurrentUserId()));
    }
}
