package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.share.SaveToFolderRequest;
import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.share.SaveToFolderResponse;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.security.CustomUserDetails;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
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

    private UUID userId(Authentication a) { return ((CustomUserDetails) a.getPrincipal()).getAccount().getId(); }

    @PostMapping
    public ApiResponse<ShareResponse> createShare(@RequestBody ShareRequest request, Authentication authentication) {
        var uid = userId(authentication);
        if (request.getDocumentId() != null)
            return ApiResponse.success(shareService.shareDocument(request, uid));
        return ApiResponse.success(shareService.shareFolder(request, uid));
    }

    @GetMapping("/owner")
    public ApiResponse<List<ShareResponse>> getSharesByOwner(Authentication authentication) {
        return ApiResponse.success(shareService.getSharesByOwner(userId(authentication)));
    }

    @GetMapping("/shared-with-me")
    public ApiResponse<List<ShareResponse>> getSharesWithMe(Authentication authentication) {
        return ApiResponse.success(shareService.getSharesWithMe(userId(authentication)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteShare(@PathVariable UUID id, Authentication authentication) {
        shareService.removeShare(id, userId(authentication));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/token/{shareToken}")
    public ApiResponse<Void> deleteShareByToken(@PathVariable String shareToken, Authentication authentication) {
        shareService.removeShareByToken(shareToken, userId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/{shareId}/save")
    public ApiResponse<SaveToFolderResponse> saveToMyFolder(
            @PathVariable UUID shareId, @RequestBody SaveToFolderRequest request, Authentication authentication) {
        return ApiResponse.success(shareService.saveToMyFolder(shareId, request.getFolderId(), request.getTitle(),
                request.getDescription(), userId(authentication)));
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
