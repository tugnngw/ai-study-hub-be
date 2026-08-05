package com.tugnw.aistudy.service;

import com.tugnw.aistudy.domain.dto.quota.StorageQuota;

import java.util.UUID;

/**
 * Nơi duy nhất tính storage:
 *   limit     = subscription.maxStorageGb * 1024^3  (snapshot, không đọc plan)
 *   used      = account.usedStorageBytes           (cộng khi upload, trừ khi permanent delete)
 *   remaining = max(0, limit - used)
 *   overQuota = used > limit
 *
 * Caller (upload/delete) không được tự tính hay tự cập nhật con số.
 */
public interface StorageQuotaService {

    StorageQuota getQuota(UUID accountId);

    /**
     * Đặt chỗ dung lượng cho upload (side-effect: tăng usedStorageBytes NGAY).
     * Lock Account (PESSIMISTIC_WRITE từ SELECT) → serialize mọi upload cùng account.
     * Vượt quota → RuntimeException + rollback (used không đổi).
     * Gọi trong cùng transaction với toàn bộ upload — atomic theo account.
     */
    void reserveStorage(UUID accountId, long incomingBytes);

    /**
     * Trừ bytes khỏi account.usedStorageBytes — gọi khi PERMANENT delete.
     * Không bao giờ âm.
     */
    void subtractUsedBytes(UUID accountId, long bytes);
}
