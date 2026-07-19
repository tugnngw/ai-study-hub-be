package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.folder.FolderCreateRequest;
import com.tugnw.aistudy.domain.dto.folder.FolderResponse;
import com.tugnw.aistudy.domain.dto.folder.FolderUpdateRequest;
import com.tugnw.aistudy.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RestController
@RequestMapping("/api/folder")
@RequiredArgsConstructor
@Validated
public class FolderController {

    private final FolderService folderService;

    @PostMapping("/create")
    public ResponseEntity<FolderResponse> createFolder(
            @RequestBody @Valid FolderCreateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.createFolder(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/getall")
    public ResponseEntity<List<FolderResponse>> getFolders(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        List<FolderResponse> responses = folderService.getFoldersByOwner(ownerId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/getbyid/{id}")
    public ResponseEntity<FolderResponse> getFolderById(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.getFolderById(id, ownerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable UUID id,
            @RequestBody FolderUpdateRequest request,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        FolderResponse response = folderService.updateFolder(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID ownerId = getCurrentUserId(authentication);
        folderService.deleteFolder(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy user ID từ Authentication (điều chỉnh nếu CustomUserDetails khác)
     */
    private UUID getCurrentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }

        throw new RuntimeException("User no login");
    }
}