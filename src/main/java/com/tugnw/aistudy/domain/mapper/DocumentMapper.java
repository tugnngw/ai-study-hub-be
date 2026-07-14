package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "summary", ignore = true) // AI sẽ generate sau
    @Mapping(target = "rejectReason", source = "rejectReason")
    DocumentResponse toResponse(Document document);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cloudinaryUrl", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "mimeType", ignore = true)
    @Mapping(target = "checksum", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "totalPages", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Document toEntity(DocumentUploadRequest request);
}