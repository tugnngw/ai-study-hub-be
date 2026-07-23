package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.share.SaveToFolderRequest;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.share.SaveToFolderResponse;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.CurrentUserService;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {
    private final ShareService shareService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public ApiResponse<ShareResponse> createShare(@RequestBody ShareRequest request) {
        if (request.getDocumentId() != null)
            return ApiResponse.success(shareService.shareDocument(request, currentUserService.getCurrentUserId()));
        return ApiResponse.success(shareService.shareFolder(request, currentUserService.getCurrentUserId()));
    }

    @GetMapping("/owner")
    public ApiResponse<List<ShareResponse>> getSharesByOwner() {
        return ApiResponse.success(shareService.getSharesByOwner(currentUserService.getCurrentUserId()));
    }

    @GetMapping("/shared-with-me")
    public ApiResponse<List<ShareResponse>> getSharesWithMe() {
        return ApiResponse.success(shareService.getSharesWithMe(currentUserService.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteShare(@PathVariable UUID id) {
        shareService.removeShare(id, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/token/{shareToken}")
    public ApiResponse<Void> deleteShareByToken(@PathVariable String shareToken) {
        shareService.removeShareByToken(shareToken, currentUserService.getCurrentUserId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{shareId}/save")
    public ApiResponse<SaveToFolderResponse> saveToMyFolder(
            @PathVariable UUID shareId,
            @RequestBody SaveToFolderRequest request) {
        return ApiResponse.success(shareService.saveToMyFolder(shareId, request.getFolderId(), request.getTitle(),
                request.getDescription(), currentUserService.getCurrentUserId()));
    }

    @GetMapping("/{shareToken}/link")
    public ApiResponse<Map<String, String>> getShareLink(@PathVariable String shareToken) {
        return ApiResponse.success(Map.of("url", shareService.getShareLinkByToken(shareToken)));
    }

    @GetMapping("/{shareToken}/download")
    public ApiResponse<Map<String, String>> getDownloadUrl(@PathVariable String shareToken) {
        return ApiResponse.success(Map.of("url", shareService.getDownloadUrlByToken(shareToken)));
    }
}
