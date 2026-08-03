package com.tugnw.aistudy.domain.dto.payment;

import com.tugnw.aistudy.domain.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PaymentStatusResponse {
    private Long orderCode;
    private PaymentStatus status;
    private Long amount;
    private Instant createdAt;
    private Instant updatedAt;
    private String planName;
    private String transactionId;

    // Helper methods for client
    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED || status == PaymentStatus.EXPIRED;
    }

    public String getStatusMessage() {
        switch (status) {
            case PAID: return "Thanh toán thành công";
            case PENDING: return "Đang chờ thanh toán";
            case FAILED: return "Thanh toán thất bại";
            case CANCELLED: return "Đã hủy thanh toán";
            case EXPIRED: return "Thanh toán đã hết hạn";
            default: return "Không xác định";
        }
    }
}