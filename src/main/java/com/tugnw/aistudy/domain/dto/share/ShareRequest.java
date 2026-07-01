package com.tugnw.aistudy.domain.dto.share;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class ShareRequest {
    private UUID folderId;
    private String email;
    private String username;
    private String visibility = "private";
    private String password;
    private Boolean isPublic = false;
    private Instant expiresAt;
    private Integer downloadLimit;
    private String allowedDomains;
    private String customMessage;
}
