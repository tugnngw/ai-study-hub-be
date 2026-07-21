package com.tugnw.aistudy.service;

import java.util.UUID;

/**
 * Service to persist document AI processing status changes.
 * Each method runs in its own transaction via REQUIRES_NEW
 * so the status change is committed even if the caller's transaction rolls back.
 */
public interface RagStatusService {

    /**
     * Set document aiStatus = FAILED in a new independent transaction.
     * Safe to call from within any transactional context.
     */
    void markProcessingFailed(UUID documentId);
}
