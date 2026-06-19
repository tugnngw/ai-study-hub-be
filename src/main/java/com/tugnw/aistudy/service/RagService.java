package com.tugnw.aistudy.service;

import org.springframework.web.multipart.MultipartFile;

public interface RagService {
    String processAndSaveDocument(MultipartFile file, Long id) throws Exception;
    String askQuestion(Long id, String question) throws Exception;
}