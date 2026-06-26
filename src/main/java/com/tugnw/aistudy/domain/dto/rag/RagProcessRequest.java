package com.tugnw.aistudy.domain.dto.rag;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RagProcessRequest {

    /**
     * ID của tài liệu đã được lưu trong Database (bảng document).
     * Yêu cầu Frontend gọi API tạo bản ghi Document trước (lưu Cloudinary URL vào DB),
     * sau đó mới lấy cái ID đó truyền vào đây để bắt đầu luồng trích xuất AI.
     */
    @NotNull(message = "Document ID không được để trống")
    private String documentId;
}