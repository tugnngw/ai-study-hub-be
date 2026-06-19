package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.dto.document.DocumentUploadRequest;
import com.tugnw.aistudy.domain.dto.document.DocumentUpdateRequest;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.mapper.DocumentMapper;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.CloudinaryService;
import com.tugnw.aistudy.service.DocumentService;
import com.tugnw.aistudy.service.RagService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;
    private final CloudinaryService cloudinaryService;
    private final RagService ragService; // Đã thêm RagService

    @Override
    public DocumentResponse uploadDocument(UUID ownerId, DocumentUploadRequest request) {
        MultipartFile file = request.getFile();

        // Validate file size (max 50MB)
        long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("Kích thước file vượt quá giới hạn 50MB");
        }

        // ĐÃ FIX: Khóa chặt định dạng chỉ cho phép PDF để tránh Crash hệ thống AI
        String contentType = file.getContentType();
        boolean allowedType = contentType != null && contentType.equals("application/pdf");
        
        if (!allowedType) {
            throw new RuntimeException("Định dạng không hợp lệ. Hệ thống AI hiện tại chỉ hỗ trợ file PDF.");
        }

        // TODO: Check total user storage (max 1GB)

        // Upload file to Cloudinary
        var uploadResult = cloudinaryService.upload(file);

        Document document = documentMapper.toEntity(request);
        document.setOwnerId(ownerId);
        document.setCloudinaryUrl((String) uploadResult.get("url"));
        document.setPublicId((String) uploadResult.get("public_id"));
        document.setMimeType(contentType);
        document.setFileSize(file.getSize());
        document.setStatus("processing"); // Trạng thái chờ AI xử lý

        Document savedDocument = documentRepository.save(document);

        // KÍCH HOẠT AI RAG
        try {
            ragService.processAndSaveDocument(file, savedDocument.getId());
            // Cập nhật thành active nếu AI đọc và băm nhỏ thành công
            savedDocument.setStatus("active");
            documentRepository.save(savedDocument);
        } catch (Exception e) {
            // Bắt lỗi nếu file PDF bị hỏng hoặc lỗi gọi Gemini API
            throw new RuntimeException("Lỗi trong quá trình AI xử lý tài liệu: " + e.getMessage());
        }

        return documentMapper.toResponse(savedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByOwner(UUID ownerId) {
        List<Document> documents = documentRepository
                .findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId);

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId) {
        // TODO: Check owner has permission to access this folder
        List<Document> documents = documentRepository
                .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);

        return documents.stream()
                .map(documentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You do not have permission to access this document");
        }

        return documentMapper.toResponse(document);
    }

    @Override
    public DocumentResponse updateDocument(Long id, UUID ownerId, DocumentUpdateRequest request) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You do not have permission to update this document");
        }

        if (request.getTitle() != null) document.setTitle(request.getTitle());
        if (request.getDescription() != null) document.setDescription(request.getDescription());
        if (request.getFolderId() != null) document.setFolderId(request.getFolderId());
        if (request.getSubjectId() != null) document.setSubjectId(request.getSubjectId());

        Document updatedDocument = documentRepository.save(document);
        return documentMapper.toResponse(updatedDocument);
    }

    @Override
    public void deleteDocument(Long id, UUID ownerId) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("You do not have permission to delete this document");
        }

        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Override
    public String getDocumentDownloadUrl(Long id, UUID ownerId) {
        // TODO: Implement
        return null;
    }

    @Override
    public String generateShareableLink(Long id, UUID ownerId) {
        // TODO: Implement shareable link logic
        return null;
    }
}