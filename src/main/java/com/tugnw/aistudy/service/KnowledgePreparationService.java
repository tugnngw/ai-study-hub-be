package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.entity.Document;

import java.util.List;

/**
 * Shared knowledge preparation pipeline for all AI features.
 * Prepares knowledge from documents by ensuring each has a summary,
 * then merges all summaries into a single markdown string.
 *
 * Workflow:
 * - For each document: if summary exists and !force -> use cached summary
 * - Else: extract text -> generate markdown summary -> overwrite document.summary
 * - After all documents have summary: merge all summaries -> return merged markdown
 *
 * All future AI features (AI Summary, Flashcard, Quiz, MindMap, Study Guide)
 * must reuse this service instead of duplicating document extraction logic.
 *
 * Caller is responsible for resolving and authorizing documents before calling.
 */
public interface KnowledgePreparationService {

    /**
     * Prepare knowledge from pre-authorized documents using summary-based pipeline.
     *
     * @param documents Pre-resolved and authorized documents
     * @param force     If true, regenerate summaries even if they already exist
     * @return Merged markdown string of all document summaries
     * @throws Exception if processing fails
     */
    String prepareKnowledge(List<Document> documents, boolean force) throws Exception;
}