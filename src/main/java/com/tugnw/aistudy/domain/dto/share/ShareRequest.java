package com.tugnw.aistudy.domain.dto.share;

import lombok.Data;
import java.util.UUID;

@Data
public class ShareRequest {
    private UUID folderId;
    private UUID documentId;
    private String email;
    private String username;
    private String visibility = "private";
}
