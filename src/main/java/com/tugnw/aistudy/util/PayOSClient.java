package com.tugnw.aistudy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tugnw.aistudy.domain.dto.payment.WebhookPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PayOSClient {

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @AllArgsConstructor
    public static class CheckoutResult {
        private String checkoutUrl;
        private String qrCode;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PAYOS_API_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("=== PAYOS CONFIGURATION ===");
        log.info("Client ID: {}", clientId);
        log.info("API Key: {}", apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "null");
        log.info("Checksum Key: {}", checksumKey != null ? "***" + checksumKey.substring(Math.max(0, checksumKey.length() - 4)) : "null");
        log.info("Return URL: {}", returnUrl);
        log.info("Cancel URL: {}", cancelUrl);
        log.info("============================");
    }

    /**
     * Remove accents from Vietnamese text
     */
    private String removeAccent(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(normalized).replaceAll("");
        result = result.replaceAll("đ", "d");
        result = result.replaceAll("Đ", "D");
        return result;
    }

    /**
     * Clean description for PayOS: no accent, no space, max 25 chars, alphanumeric + underscore only
     */
    private String cleanDescription(String input) {
        // Remove accents
        String cleaned = removeAccent(input);
        // Replace spaces with underscore
        cleaned = cleaned.replaceAll(" ", "_");
        // Remove special characters (keep only alphanumeric and underscore)
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9_]", "");
        // Limit to 25 characters
        if (cleaned.length() > 25) {
            cleaned = cleaned.substring(0, 25);
        }
        return cleaned;
    }

    public CheckoutResult createCheckoutUrl(long amount, String userId, Long orderCode, String planName) {
        try {
            // ⚠️ orderCode must be <= 2,147,483,647
            int safeOrderCode;
            if (orderCode > Integer.MAX_VALUE) {
                safeOrderCode = (int) (orderCode % 2_000_000_000L);
            } else {
                safeOrderCode = orderCode.intValue();
            }
            if (safeOrderCode < 100000) {
                safeOrderCode += 100000;
            }

            log.info("Original orderCode: {}, Safe orderCode: {}", orderCode, safeOrderCode);

            // ✅ Clean description: no accent, no space, max 25 chars
            String description = cleanDescription("Thanh toan goi " + planName);
            if (description.isEmpty()) {
                description = "Payment_" + safeOrderCode;
            }

            log.info("Cleaned description: {}", description);

            // Set expiration time: 3 minutes from now
            long expiredAtTimestamp = System.currentTimeMillis() / 1000 + 180;

            // Generate signature (WITHOUT expiredAt - not supported in signature by PayOS)
            String signatureData = "amount=" + amount 
                    + "&cancelUrl=" + cancelUrl 
                    + "&description=" + description 
                    + "&orderCode=" + safeOrderCode 
                    + "&returnUrl=" + returnUrl;
            String signature = hmacSha256Hex(signatureData, checksumKey);

            // Build request body - REQUIRED FIELDS WITH SIGNATURE
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("orderCode", safeOrderCode);
            requestBody.put("amount", amount);
            requestBody.put("description", description);
            requestBody.put("returnUrl", returnUrl);
            requestBody.put("cancelUrl", cancelUrl);
            requestBody.put("expiredAt", expiredAtTimestamp); // Thêm expiredAt vào body (không có trong signature)
            requestBody.put("signature", signature);
            
            // items must be an array, not a single object
            List<Map<String, Object>> items = List.of(
                Map.of("name", description, "quantity", 1, "price", (int) amount)
            );
            requestBody.put("items", items);

            log.info("=== CREATING PAYMENT LINK ===");
            log.info("Request body: {}", objectMapper.writeValueAsString(requestBody));

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call PayOS API
            ResponseEntity<Map> response = restTemplate.exchange(
                    PAYOS_API_URL,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("Response status: {}", response.getStatusCode());
            log.info("Response body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String code = (String) responseBody.get("code");

                if (!"00".equals(code)) {
                    String desc = (String) responseBody.get("desc");
                    log.error("PayOS API error: code={}, desc={}", code, desc);
                    throw new RuntimeException("PayOS error: " + desc);
                }

                Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                if (responseData == null) {
                    log.error("PayOS response data is null: {}", responseBody);
                    throw new RuntimeException("PayOS returned empty data");
                }

                String checkoutUrl = (String) responseData.get("checkoutUrl");
                if (checkoutUrl == null || checkoutUrl.isEmpty()) {
                    log.error("Checkout URL is null or empty: {}", responseData);
                    throw new RuntimeException("PayOS did not return checkout URL");
                }

                log.info("✅ Created payOS payment link: {}", checkoutUrl);
                return new CheckoutResult(checkoutUrl, null);
            } else {
                log.error("PayOS API error: status={}, body={}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed to create payment link: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error creating payOS payment link", e);
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    public WebhookPayload parseWebhookPayload(String payload) {
        try {
            log.info("Parsing webhook payload");
            return objectMapper.readValue(payload, WebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            throw new RuntimeException("Failed to parse webhook payload", e);
        }
    }

    public boolean verifySignature(String payload, String signature) {
        try {
            if (payload == null || payload.isEmpty() || signature == null || signature.isEmpty()) {
                log.error("Payload or signature is null/empty");
                return false;
            }

            // PayOS signs the JSON string of the "data" field with sorted keys
            var root = objectMapper.readTree(payload);
            JsonNode dataNode = root.get("data");
            if (dataNode == null) {
                log.error("No 'data' field in webhook payload");
                return false;
            }

            // Sort keys alphabetically and convert to query-string format
            StringBuilder sb = new StringBuilder();
            java.util.List<String> fieldNames = new java.util.ArrayList<>();
            dataNode.fieldNames().forEachRemaining(fieldNames::add);
            java.util.Collections.sort(fieldNames);
            for (int i = 0; i < fieldNames.size(); i++) {
                String key = fieldNames.get(i);
                JsonNode value = dataNode.get(key);
                if (i > 0) sb.append("&");
                String val = value.isNull() ? "" : value.asText();
                sb.append(key).append("=").append(val);
            }
            String signData = sb.toString();

            log.info("Sign data: {}", signData);
            String calculated = hmacSha256Hex(signData, checksumKey);
            log.info("Calculated signature: {}", calculated);
            log.info("Received signature: {}", signature);

            boolean isValid = calculated.equalsIgnoreCase(signature);
            log.info("Signature valid: {}", isValid);

            return isValid;
        } catch (Exception e) {
            log.error("Failed to verify signature", e);
            return false;
        }
    }

    private String removeSignatureField(String payload) {
        try {
            var jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("signature")) {
                ObjectNode node = (ObjectNode) jsonNode;
                node.remove("signature");
                return objectMapper.writeValueAsString(node);
            }
            return payload;
        } catch (Exception e) {
            log.warn("Could not remove signature from payload: {}", e.getMessage());
            return payload;
        }
    }

    private String hmacSha256Hex(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(spec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}