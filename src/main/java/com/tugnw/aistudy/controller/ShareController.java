package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.domain.dto.share.ShareRequest;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {
    private final ShareService shareService;

    @PostMapping
    public ResponseEntity<ShareResponse> createShare(@RequestBody ShareRequest request, Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(shareService.shareFolder(request, ownerId));
    }

    @GetMapping("/owner")
    public ResponseEntity<List<ShareResponse>> getSharesByOwner(Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        return ResponseEntity.ok(shareService.getSharesByOwner(ownerId));
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<ShareResponse>> getSharesWithMe(Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        return ResponseEntity.ok(shareService.getSharesWithMe(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShare(@PathVariable Long id, Authentication authentication) {
        UUID ownerId = getCurrentUserId(authentication);
        shareService.removeShare(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCurrentUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
            return userDetails.getAccount().getId();
        }
        throw new RuntimeException("Unauthorized");
    }
}
