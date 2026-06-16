package com.tugnw.aistudy.controller;

import com.tugnw.aistudy.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*") 
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    // Endpoint 1: FE gọi cái này khi bấm nút Upload
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentId") Long documentId) { 
        try {
            String result = ragService.processAndSaveDocument(file, documentId);
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Endpoint 2: FE gọi cái này khi chat
    @PostMapping("/ask")
    public ResponseEntity<?> askQuestion(
            @RequestParam("documentId") Long documentId,
            @RequestParam("question") String question) {
        try {
            String answer = ragService.askQuestion(documentId, question);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}