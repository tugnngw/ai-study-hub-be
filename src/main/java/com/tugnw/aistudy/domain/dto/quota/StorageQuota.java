package com.tugnw.aistudy.domain.dto.quota;

/**
 * Storage quota snapshot — backend là nơi DUY NHẤT tính storage.
 * Frontend chỉ hiển thị, không tự tính.
 */
public record StorageQuota(
        long storageUsedBytes,
        long storageLimitBytes,
        long storageRemainingBytes,
        boolean overQuota
) {
    public static StorageQuota freeUnlimited() {
        return new StorageQuota(0L, -1L, -1L, false);
    }
}
