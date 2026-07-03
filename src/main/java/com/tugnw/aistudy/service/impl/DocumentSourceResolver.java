package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentSourceResolver {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;

    /**
     * Resolve documents by a list of specific document IDs.
     * Respects soft delete (deletedAt IS NULL).
     * Returns deterministically ordered list matching input order.
     *
     * @param docIds List of document IDs to fetch
     * @return List of non-deleted documents, ordered by input list precedence
     */
    public List<Document> resolveByDocumentIds(List<UUID> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch documents (soft delete filtering handled by repository method)
        List<Document> documents = documentRepository.findAllById(docIds);

        // Build deterministic ordering map to avoid O(n^2) with repeated indexOf calls
        Map<UUID, Integer> idOrderMap = new HashMap<>();
        for (int i = 0; i < docIds.size(); i++) {
            idOrderMap.put(docIds.get(i), i);
        }

        // Sort documents to match input order
        return documents.stream()
            .sorted(Comparator.comparing(doc -> idOrderMap.getOrDefault(doc.getId(), Integer.MAX_VALUE)))
            .toList();
    }

    /**
     * Resolve all documents within a folder.
     * Respects soft delete (deletedAt IS NULL).
     * Returns ordered list by creation date descending.
     *
     * @param folderId Folder ID
     * @return List of non-deleted documents in the folder
     */
    public List<Document> resolveByFolderId(UUID folderId) {
        if (folderId == null) {
            return Collections.emptyList();
        }

        // Existing repository method already filters deleted records and orders by creation date
        return documentRepository.findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);
    }
}
