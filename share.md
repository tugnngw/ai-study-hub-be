# Tài Liệu Hệ Thống Chia Sẻ - Share Module Documentation

## 📋 Mục Lục
1. [Tổng Quan Hệ Thống](#tổng-quan-hệ-thống)
2. [Kiến Trúc & Luồng Dữ Liệu](#kiến-trúc--luồng-dữ-liệu)
3. [Backend - Chi Tiết Code](#backend---chi-tiết-code)
4. [Frontend - Chi Tiết Code](#frontend---chi-tiết-code)
5. [Database Schema](#database-schema)
6. [Lý Do Thiết Kế](#lý-do-thiết-kế)

---

## 🎯 Tổng Quan Hệ Thống

Module Share cho phép người dùng **chia sẻ tài liệu/folder** với người khác thông qua link hoặc email.

### Các Tính Năng Chính:
- ✅ Chia sẻ folder hoặc document
- ✅ Chia sẻ với user cụ thể (private) hoặc public link
- ✅ Quản lý "Shared by me" và "Shared with me"
- ✅ Thu hồi quyền chia sẻ (revoke)
- ✅ Lưu shared item vào folder của mình
- ✅ Share token (UUID) để bảo mật
- ✅ Expiration time và visibility control

### Công Nghệ Sử Dụng:
- **Backend**: Spring Boot, JPA/Hibernate, PostgreSQL
- **Frontend**: React, TypeScript, TanStack Router
- **Security**: UUID tokens (không thể đoán)

---

## 🏗️ Kiến Trúc & Luồng Dữ Liệu

### Luồng Chia Sẻ Hoàn Chỉnh:

```
[User A - Owner]
  ↓ Click "Share" trên folder/document
[Frontend: ShareDocumentDialog]
  ↓ Nhập email User B hoặc chọn "Public"
  ↓ POST /api/shares {folderId/documentId, email, visibility}
[ShareController]
  ↓ shareFolder() hoặc shareDocument()
[ShareServiceImpl]
  ↓ 1. Validate ownership (User A owns folder/document)
  ↓ 2. Tìm User B bằng email (nếu private)
  ↓ 3. Tạo Share entity với UUID token
  ↓ 4. Set visibility (private/public)
  ↓ 5. Lưu vào database
[Database]
  ↓ Insert vào bảng share
[Frontend]
  ↑ Nhận shareToken
  ↑ Hiển thị link: https://app.com/shared/{shareToken}
  
[User B - Recipient]
  ↓ Mở link hoặc vào "Shared with me"
  ↓ GET /api/shares/shared-with-me
[ShareController]
  ↓ getSharesWithMe(userId)
[ShareServiceImpl]
  ↓ Query: WHERE shared_account_id = userId AND revoked = false
[Frontend]
  ↑ Hiển thị danh sách items được chia sẻ
```

---

## 💻 Backend - Chi Tiết Code

### 1. ShareController.java

**Vai trò**: REST Controller cho share operations

```java
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {
    private final ShareService shareService;
```

#### Endpoint 1: Tạo Share (cho Folder)

```java
@PostMapping
public ResponseEntity<ShareResponse> createShare(
        @RequestBody ShareRequest request, 
        Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    return ResponseEntity.ok(shareService.shareFolder(request, ownerId));
}
```

**Giải thích:**
- `@PostMapping`: POST /api/shares
- Nhận `ShareRequest` với: `folderId`, `sharedWithEmail`, `visibility`
- `getCurrentUserId()`: Lấy owner từ JWT token - **không trust client**

**ShareRequest DTO:**
```java
public class ShareRequest {
    private UUID folderId;      // Nullable
    private UUID documentId;    // Nullable (chia sẻ folder HOẶC document)
    private String sharedWithEmail; // Email người nhận (nullable nếu public)
    private String visibility;  // "private" hoặc "public"
}
```

#### Endpoint 2: Get "Shared by Me"

```java
@GetMapping("/owner")
public ResponseEntity<List<ShareResponse>> getSharesByOwner(Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    return ResponseEntity.ok(shareService.getSharesByOwner(ownerId));
}
```

**Giải thích:**
- Lấy tất cả shares mà user hiện tại là owner
- Query: `WHERE owner_id = ownerId`

#### Endpoint 3: Get "Shared with Me"

```java
@GetMapping("/shared-with-me")
public ResponseEntity<List<ShareResponse>> getSharesWithMe(Authentication authentication) {
    UUID userId = getCurrentUserId(authentication);
    return ResponseEntity.ok(shareService.getSharesWithMe(userId));
}
```

**Giải thích:**
- Lấy tất cả shares được chia sẻ cho user hiện tại
- Query: `WHERE shared_account_id = userId AND revoked = false`

#### Endpoint 4: Delete Share (Revoke)

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteShare(
        @PathVariable Long id, 
        Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    shareService.removeShare(id, ownerId);
    return ResponseEntity.noContent().build();
}
```

**Giải thích:**
- Chỉ owner mới có thể revoke share
- Service sẽ validate ownership trước khi xóa
- HTTP 204 No Content khi thành công

---

### 2. ShareServiceImpl.java - Logic Nghiệp Vụ

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ShareServiceImpl implements ShareService {
    private final ShareRepository shareRepository;
    private final AccountRepository accountRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
```

#### Method: shareFolder()

```java
@Override
@Transactional
public ShareResponse shareFolder(ShareRequest request, UUID ownerId) {
    // 1. Validate folder exists và thuộc về owner
    Folder folder = folderRepository.findById(request.getFolderId())
            .orElseThrow(() -> new RuntimeException("Folder not found"));
    
    Account owner = accountRepository.findById(ownerId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    // **KIỂM TRA BẢO MẬT QUAN TRỌNG**
    if (!folder.getOwner().getId().equals(ownerId)) {
        throw new SecurityException("Cannot share a folder you do not own");
    }

    // 2. Xử lý recipient (nếu là private share)
    Account sharedAccount = null;
    if ("private".equals(request.getVisibility())) {
        if (request.getSharedWithEmail() == null) {
            throw new IllegalArgumentException("Email is required for private sharing");
        }
        sharedAccount = accountRepository.findByEmail(request.getSharedWithEmail())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));
    }

    // 3. Tạo Share entity
    Share share = Share.builder()
            .owner(owner)
            .folder(folder)
            .sharedAccount(sharedAccount)  // Null nếu public
            .visibility(request.getVisibility())
            .revoked(false)
            .build();

    Share savedShare = shareRepository.save(share);
    return mapToShareResponse(savedShare);
}
```

**Phân tích chi tiết:**

**1. Ownership Validation:**
```java
if (!folder.getOwner().getId().equals(ownerId)) {
    throw new SecurityException("Cannot share a folder you do not own");
}
```
- **CỰC KỲ QUAN TRỌNG** cho security
- Ngăn User A chia sẻ folder của User B
- **IDOR Prevention**: Insecure Direct Object Reference

**2. Public vs Private Logic:**
```java
if ("private".equals(request.getVisibility())) {
    // Phải có email và user phải tồn tại
    sharedAccount = accountRepository.findByEmail(request.getSharedWithEmail())
            .orElseThrow(...);
}
```
- **Private share**: `sharedAccount` != null
- **Public share**: `sharedAccount` = null, ai có link đều truy cập được

**3. Tại sao không throw exception nếu email không tồn tại?**
- **Bảo mật**: Không nên leak thông tin user có trong hệ thống
- **UX**: Frontend nên validate email trước khi gửi

---

### 3. Share Entity

```java
@Entity
@Table(name = "share")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Share {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Account owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_account_id")  // NULLABLE
    private Account sharedAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")  // NULLABLE
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")  // NULLABLE
    private Document document;

    @Column(name = "share_token", unique = true, length = 36)
    private String shareToken;

    @Column(name = "visibility", nullable = false, length = 50)
    @Builder.Default
    private String visibility = "private";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.shareToken == null) {
            this.shareToken = UUID.randomUUID().toString();
        }
    }
}
```

**Giải thích Design Decisions:**

**1. Nullable Fields Strategy:**
```java
@JoinColumn(name = "folder_id")  // NULLABLE
private Folder folder;

@JoinColumn(name = "document_id")  // NULLABLE
private Document document;
```
- Một share chỉ trỏ đến **folder HOẶC document**, không cả hai
- **Tại sao không tách thành 2 tables?**
  - ✅ Đơn giản: 1 table dễ quản lý hơn 2
  - ✅ Queries đơn giản: `SELECT * FROM share WHERE owner_id = ?`
  - ❌ Trade-off: Không enforce constraint ở DB level (phải check ở code)

**2. UUID Token Generation:**
```java
@PrePersist
public void prePersist() {
    if (this.shareToken == null) {
        this.shareToken = UUID.randomUUID().toString();
    }
}
```

**Tại sao dùng UUID thay vì ID số?**

**ID số (BAD):**
```
/share/1
/share/2  ← Dễ đoán
/share/3
```
- **Enumeration Attack**: Hacker thử từ 1→1000000 để tìm shares
- Biết được có bao nhiêu shares trong hệ thống

**UUID (GOOD):**
```
/share/a3f2b8c4-1234-5678-9abc-def012345678
```
- **2^128 combinations** - không thể brute force
- Không leak thông tin về số lượng shares
- **Security by obscurity** (lớp bảo mật bổ sung)

**3. Revoked Flag thay vì DELETE:**
```java
@Column(name = "revoked", nullable = false)
private Boolean revoked = false;
```

**Tại sao không xóa thẳng?**
- ✅ **Audit trail**: Giữ lịch sử ai chia sẻ gì với ai
- ✅ **Recovery**: Có thể "un-revoke" nếu cần
- ✅ **Analytics**: Tracking share patterns
- ✅ **Compliance**: Yêu cầu pháp lý giữ log

**4. Expiration Time:**
```java
@Column(name = "expires_at")
private Instant expiresAt;  // NULLABLE
```
- Nullable = không hết hạn
- Non-null = tự động revoke sau thời gian này
- **TODO**: Cần background job để auto-revoke expired shares

---

## 🎨 Frontend - Chi Tiết Code

### 1. shareApi.ts - API Service

```typescript
export const sharesApi = {
  getSharedWithMe: async (): Promise<SharedWithMeItem[]> => {
    const response = await api<ShareResponse[]>("/api/shares/shared-with-me");
    return response.map(mapShareResponseToSharedWithMe);
  },

  getSharedByMe: async (): Promise<SharedByMeItem[]> => {
    const response = await api<ShareResponse[]>("/api/shares/owner");
    return response.map(mapShareResponseToSharedByMe);
  },

  deleteShared: (shareToken: string) =>
      api<void>(`/api/shares/${shareToken}`, { method: "DELETE" }),

  getShareLink: (shareToken: string) =>
      api<{ url: string }>(`/api/shares/${shareToken}/link`),
};
```

**Mapping Functions:**
```typescript
function mapShareResponseToSharedWithMe(resp: ShareResponse): SharedWithMeItem {
  return {
    id: resp.shareToken,  // Dùng token làm ID
    name: resp.folderName || resp.documentTitle || "Unknown",
    sharedBy: {
      name: resp.ownerUsername || resp.ownerEmail,
      avatarUrl: null,
    },
    time: formatRelativeTime(resp.createdAt),
    order: new Date(resp.createdAt).getTime(),
  };
}
```

**Giải thích:**
- `shareToken` làm ID: Unique và dùng cho routing
- Fallback chain: `folderName || documentTitle || "Unknown"`
- `order`: Timestamp để sort

---

## 🗄️ Database Schema

```sql
CREATE TABLE share (
    id BIGSERIAL PRIMARY KEY,
    
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    shared_account_id UUID REFERENCES account(id) ON DELETE CASCADE,
    
    folder_id UUID REFERENCES folder(id) ON DELETE CASCADE,
    document_id UUID REFERENCES document(id) ON DELETE CASCADE,
    
    share_token VARCHAR(36) UNIQUE NOT NULL,
    visibility VARCHAR(50) NOT NULL DEFAULT 'private',
    
    expires_at TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_one_resource CHECK (
        (folder_id IS NOT NULL AND document_id IS NULL) OR
        (folder_id IS NULL AND document_id IS NOT NULL)
    )
);

CREATE INDEX idx_share_owner ON share(owner_id);
CREATE INDEX idx_share_recipient ON share(shared_account_id);
CREATE INDEX idx_share_token ON share(share_token);
CREATE INDEX idx_share_revoked ON share(revoked) WHERE revoked = false;
```

**Giải thích:**

**1. CHECK Constraint:**
```sql
CONSTRAINT check_one_resource CHECK (
    (folder_id IS NOT NULL AND document_id IS NULL) OR
    (folder_id IS NULL AND document_id IS NOT NULL)
)
```
- **Enforce ở DB level**: Không thể có cả folder_id và document_id cùng lúc
- Backup cho validation ở code

**2. Partial Index:**
```sql
CREATE INDEX idx_share_revoked ON share(revoked) WHERE revoked = false;
```
- Index chỉ cho shares chưa revoke
- **Performance**: Queries thường filter `WHERE revoked = false`
- Giảm size index (không index rows đã revoke)

**3. ON DELETE CASCADE:**
- Xóa owner → xóa tất cả shares của họ
- Xóa folder/document → xóa tất cả shares liên quan
- **Tự động cleanup** - không có orphaned records

---

## 🤔 Lý Do Thiết Kế

### 1. Tại sao dùng một bảng Share cho cả Folder và Document?

**Alternative: 2 bảng riêng**
```sql
folder_share (id, folder_id, ...)
document_share (id, document_id, ...)
```

**Ưu điểm 1 bảng:**
- ✅ **Đơn giản**: Ít code hơn, ít bugs hơn
- ✅ **Unified API**: Frontend chỉ gọi một endpoint
- ✅ **Dễ mở rộng**: Thêm share type mới (playlist, note) chỉ cần thêm column

**Nhược điểm:**
- ❌ Phải check NULL ở code
- ❌ Không enforce constraint hoàn toàn ở DB

**Verdict**: 1 bảng phù hợp cho scale nhỏ-trung bình

### 2. Tại sao Token thay vì ID?

**Share link với ID:**
```
https://app.com/shared/123
```
- User thử `/shared/124`, `/shared/125` → tìm được shares khác

**Share link với UUID:**
```
https://app.com/shared/a3f2b8c4-1234-5678-9abc-def012345678
```
- **Không thể đoán** - 2^128 combinations
- Thêm lớp security ngoài authentication

### 3. Tại sao Revoke thay vì Delete?

**Hard Delete:**
```sql
DELETE FROM share WHERE id = ?;
```
- Mất lịch sử vĩnh viễn

**Soft Delete (Revoke):**
```sql
UPDATE share SET revoked = true WHERE id = ?;
```
- ✅ Audit trail
- ✅ Analytics
- ✅ Compliance
- ✅ Un-revoke nếu cần

---

## ✅ Security Checklist

- ✅ **Ownership validation**: Kiểm tra ở mọi operation
- ✅ **UUID tokens**: Không thể enumerate
- ✅ **Revoke flag**: Thu hồi quyền truy cập
- ⚠️ **Expiration auto-check**: Cần background job
- ⚠️ **Rate limiting**: Ngăn spam share requests
- ❌ **Share analytics**: Tracking ai access link bao nhiêu lần

---

**Tác giả**: AI Study Hub Team  
**Cập nhật**: 2026-07-02
