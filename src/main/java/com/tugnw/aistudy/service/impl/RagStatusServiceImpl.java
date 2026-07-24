package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.domain.enums.AiProcessingStatus;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.RagStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagStatusServiceImpl implements RagStatusService {

    private final DocumentRepository documentRepository;

    /**
     * Persist FAILED in a separate transaction so it survives any outer rollback.
     * REQUIRES_NEW suspends the caller's transaction, commits immediately,
     * then the caller resumes and may safely roll back without affecting this.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessingFailed(UUID documentId) {
        Document doc = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElse(null);
        if (doc == null) return;

        doc.setAiStatus(AiProcessingStatus.FAILED);
        documentRepository.save(doc);
    }
}
