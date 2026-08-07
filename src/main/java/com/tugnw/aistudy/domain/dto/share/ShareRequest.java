package com.tugnw.aistudy.domain.dto.share;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class ShareRequest {
    private UUID folderId;
    private UUID documentId;

    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    private String visibility = "private";
}
