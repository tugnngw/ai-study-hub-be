package com.tugnw.aistudy.domain.dto.share;

import lombok.Data;

import java.util.UUID;

@Data
public class ShareRequest {
    private UUID folderId;
    private String email;      // Optional: share by email
    private String username;   // Optional: share by username
    private String visibility = "private";
}
