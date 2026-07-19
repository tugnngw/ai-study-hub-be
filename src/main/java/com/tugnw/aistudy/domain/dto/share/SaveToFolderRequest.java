package com.tugnw.aistudy.domain.dto.share;

import lombok.Data;
import java.util.UUID;

@Data
public class SaveToFolderRequest {
    private UUID folderId;
    private String title;
    private String description;
}
