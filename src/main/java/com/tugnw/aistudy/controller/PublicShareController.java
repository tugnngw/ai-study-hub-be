package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.domain.dto.common.ApiResponse;
import com.tugnw.aistudy.domain.dto.share.ShareResponse;
import com.tugnw.aistudy.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicShareController {
    private final ShareService shareService;

    @GetMapping("/shared/{shareToken}")
    public ApiResponse<ShareResponse> getSharedByToken(@PathVariable String shareToken) {
        return ApiResponse.success(shareService.getShareByToken(shareToken));
    }
}
