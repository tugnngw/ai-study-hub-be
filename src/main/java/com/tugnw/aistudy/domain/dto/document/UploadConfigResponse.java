package com.tugnw.aistudy.domain.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadConfigResponse {
    private List<String> allowedExtensions;
    private Long maxFileSize; // in bytes
}
