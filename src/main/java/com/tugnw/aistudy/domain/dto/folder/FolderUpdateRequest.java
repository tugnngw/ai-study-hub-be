package com.tugnw.aistudy.domain.dto.folder;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FolderUpdateRequest {

    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;
}
