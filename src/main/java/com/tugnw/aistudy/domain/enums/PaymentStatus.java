package com.tugnw.aistudy.domain.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("Chờ thanh toán"),
    PROCESSING("Đang xử lý"),
    PAID("Đã thanh toán"),
    CANCELLED("Đã hủy"),
    FAILED("Thất bại"),
    EXPIRED("Hết hạn");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public static PaymentStatus fromValue(String value) {
        if (value == null) return PENDING;
        try {
            return PaymentStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
