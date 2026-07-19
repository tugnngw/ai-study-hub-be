package com.tugnw.aistudy.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final ObjectMapper objectMapper;

    @PostMapping("/webhook-test")
    public ResponseEntity<String> testWebhook(@RequestBody String payload) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║          DEBUG: WEBHOOK TEST RECEIVED                        ║");
        log.info("╚══════════════════════════════════════════════════════════════");
        log.info("RAW PAYLOAD: {}", payload);
        
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            log.info("PARSED JSON: {}", jsonNode.toPrettyString());
        } catch (Exception e) {
            log.error("Failed to parse JSON", e);
        }
        
        return ResponseEntity.ok("DEBUG WEBHOOK RECEIVED");
    }
}
