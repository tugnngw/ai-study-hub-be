package com.tugnw.aistudy.domain.dto.document;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Request for uploading one or more documents")
public class DocumentUploadRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 255, message = "Title must be at most 255 characters")
    @Schema(description = "Document title (applied to all files)", example = "AI Study Material", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Document description", example = "Summary of AI concepts")
    private String description;

    @Schema(description = "Folder ID where documents belong", example = "d7ff12cf-2ad0-4888-a9a1-b12de5d2bc9e")
    private UUID folderId;

    @Schema(description = "Subject ID", example = "1")
    private Long subjectId;

    @ArraySchema(schema = @Schema(description = "Files to upload (PDF, DOCX, TXT, PPTX). Max 50MB each", requiredMode = Schema.RequiredMode.REQUIRED))
    private List<MultipartFile> files;
}