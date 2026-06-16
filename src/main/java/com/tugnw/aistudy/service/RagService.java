package com.tugnw.aistudy.service;

import org.springframework.web.multipart.MultipartFile;

public interface RagService {
    String processAndSaveDocument(MultipartFile file, Long documentId) throws Exception;
    String askQuestion(Long documentId, String question) throws Exception;
}