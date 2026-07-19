package com.tugnw.aistudy.service.impl;

import com.tugnw.aistudy.domain.entity.Document;
import com.tugnw.aistudy.repository.DocumentRepository;
import com.tugnw.aistudy.service.KnowledgePreparationService;
import com.tugnw.aistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgePreparationServiceImpl implements KnowledgePreparationService {

    private final DocumentRepository documentRepository;
    private final RagService ragService;

    private static final String SUMMARY_PROMPT = """
            Generate a concise, well-structured markdown summary of the following document content.
            Extract key concepts, definitions, and important details.
            Use headings, bullet points, and bold text for emphasis.
            Keep it informative but brief — capture what a student needs to know.

            Document:
            %s
            """;

    @Override
    @Transactional
    public String prepareKnowledge(List<Document> documents, boolean force) throws Exception {
        StringBuilder merged = new StringBuilder();

        for (Document doc : documents) {
            String summary = getOrGenerateSummary(doc, force);
            merged.append("--- ").append(doc.getTitle()).append(" ---\n")
                  .append(summary).append("\n\n");
        }

        return merged.toString().trim();
    }

    private String getOrGenerateSummary(Document doc, boolean force) throws Exception {
        if (!force && doc.getSummary() != null && !doc.getSummary().trim().isEmpty()) {
            return doc.getSummary();
        }

        String rawText = ragService.extractTextFromDocument(doc.getId(), doc.getOwnerId());
        String prompt = String.format(SUMMARY_PROMPT, rawText);
        String markdownSummary = ragService.generateContent(prompt);

        doc.setSummary(markdownSummary);
        documentRepository.save(doc);

        return markdownSummary;
    }
}