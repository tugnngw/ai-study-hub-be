package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.dto.document.DocumentResponse;
import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.AdminDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminDocumentServiceImpl implements AdminDocumentService {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByStatus(String status) {
        String targetStatus = switch (status.toUpperCase()) {
            case "PENDING" -> "COMPLETED";
            case "APPROVED" -> "READY";
            case "REJECTED" -> "REJECT";
            default -> status.toUpperCase();
        };
        return documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null && d.getStatus() != null && d.getStatus().equalsIgnoreCase(targetStatus))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getTrashDocuments() {
        return documentRepository.findAll().stream()
                .filter(document -> document.getDeletedAt() != null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Override
    public void restoreDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setDeletedAt(null);
        documentRepository.save(document);
    }

    @Override
    public void approveDocument(UUID id) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setStatus("READY");
        documentRepository.save(document);
    }

    @Override
    public void rejectDocument(UUID id) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        document.setStatus("REJECT");
        documentRepository.save(document);
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setOwnerId(document.getOwnerId());
        response.setFolderId(document.getFolderId());
        response.setSubjectId(document.getSubjectId());
        response.setTitle(document.getTitle());
        response.setDescription(document.getDescription());
        response.setSummary(document.getSummary());
        response.setStatus(document.getStatus());
        response.setMimeType(document.getMimeType());
        response.setFileSize(document.getFileSize());
        response.setCloudinaryUrl(document.getCloudinaryUrl());
        response.setCreatedAt(document.getCreatedAt());
        return response;
    }
}
