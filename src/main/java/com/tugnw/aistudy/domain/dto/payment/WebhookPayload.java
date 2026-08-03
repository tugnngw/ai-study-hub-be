package com.tugnw.aistudy.domain.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {
    private String code;
    private String desc;
    private boolean success;
    private Data data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Long orderCode;
        private int amount;
        private String description;
        private String status;
        private String transactionId;
        private String cancelReason;
        private String createdAt;
        private String accountNumber;
        private String reference;
        private String transactionDateTime;
        private String paymentLinkId;
        private String counterAccountBankId;
        private String counterAccountBankName;
        private String counterAccountName;
        private String counterAccountNumber;
        private String virtualAccountName;
        private String virtualAccountNumber;
        private String currency;
    }
}