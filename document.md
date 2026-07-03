# Tài Liệu Hệ Thống Quản Lý Document

## 📋 Mục Lục
1. [Tổng Quan Hệ Thống](#tổng-quan-hệ-thống)
2. [Kiến Trúc & Luồng Dữ Liệu](#kiến-trúc--luồng-dữ-liệu)
3. [Backend - Chi Tiết Code](#backend---chi-tiết-code)
4. [Frontend - Chi Tiết Code](#frontend---chi-tiết-code)
5. [Database Schema](#database-schema)
6. [Lý Do Thiết Kế](#lý-do-thiết-kế)

---

## 🎯 Tổng Quan Hệ Thống

Module Document là **trái tim của hệ thống**, chịu trách nhiệm cho việc tải lên, lưu trữ, xử lý, và quản lý tất cả các tài liệu của người dùng.

### Các Tính Năng Chính:
- ✅ Upload file với multipart/form-data
- ✅ Lưu trữ trên Cloudinary (cloud storage)
- ✅ CRUD operations (Create, Read, Update, Delete)
- ✅ Soft delete (Thùng rác) và restore
- ✅ Ownership validation (chỉ chủ sở hữu mới được thao tác)
- ✅ Integration với Folder và Share modules
- ✅ Document status lifecycle (COMPLETED/READY/REJECT)

### Công Nghệ Sử Dụng:
- **Backend**: Spring Boot, PostgreSQL, Cloudinary SDK
- **Frontend**: React, TanStack Router, TypeScript
- **Storage**: Cloudinary (CDN + cloud storage)

---

## 🏗️ Kiến Trúc & Luồng Dữ Liệu

### Luồng Upload Document Hoàn Chỉnh:

```
[User Browser]
  ↓ Chọn file(s) từ máy tính
[Frontend: documentApi.upload()]
  ↓ Tạo FormData với file + metadata
  ↓ POST /api/documents (multipart/form-data)
[DocumentController.uploadDocument()]
  ↓ Nhận MultipartFile[] từ request
  ↓ Lấy ownerId từ Authentication (JWT)
  ↓ Gọi documentService.uploadDocuments()
[DocumentServiceImpl]
  ↓ Với mỗi file:
  ↓ 1. Tạo Document entity với status=PENDING
  ↓ 2. Save vào DB để có ID
  ↓ 3. Upload file.getBytes() lên Cloudinary
  ↓ 4. Nhận về url và publicId
  ↓ 5. Update Document: fileUrl, cloudinaryPublicId, status=COMPLETED
  ↓ 6. Save lại vào DB
[Cloudinary API]
  ↑ Trả về secure_url và public_id
[PostgreSQL]
  ↓ Lưu metadata (title, owner, folder, status, url)
[Frontend]
  ↑ Nhận List<DocumentResponse>
  ↑ Hiển thị tài liệu mới trong danh sách
```

---

## 💻 Backend - Chi Tiết Code

### 1. DocumentController.java

**Vai trò**: REST Controller - nhận HTTP requests và route đến service

```java
@RestController
@RequestMapping("/api/documents")
@Validated
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
public class DocumentController {
    private final DocumentService documentService;
    private final ShareService shareService;
```

**Giải thích annotations:**
- `@RestController`: Kết hợp `@Controller` + `@ResponseBody` - tự động serialize response thành JSON
- `@RequestMapping("/api/documents")`: Base path cho tất cả endpoints trong controller này
- `@Validated`: Enable JSR-303 validation cho request parameters
- `@CrossOrigin(*)`: Cho phép CORS từ mọi origin - **CHÚ Ý**: Production nên giới hạn cụ thể

#### Endpoint 1: Upload Document

```java
@PostMapping(consumes = {"multipart/form-data"})
public ResponseEntity<List<DocumentResponse>> uploadDocument(
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "folderId", required = false) UUID folderId,
        @RequestParam(value = "subjectId", required = false) Long subjectId,
        Authentication authentication) {

    DocumentUploadRequest request = new DocumentUploadRequest();
    request.setFiles(files);
    if (title != null) request.setTitle(title);
    if (description != null) request.setDescription(description);
    request.setFolderId(folderId);
    request.setSubjectId(subjectId);

    UUID ownerId = getCurrentUserId(authentication);
    List<DocumentResponse> responses = documentService.uploadDocuments(ownerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(responses);
}
```

**Giải thích từng dòng:**

- `consumes = {"multipart/form-data"}`: **CỰC KỲ QUAN TRỌNG**
  - Báo cho Spring Boot biết endpoint này nhận file upload
  - Không có dòng này → Spring sẽ cố parse JSON → lỗi 415 Unsupported Media Type
  
- `@RequestParam("files") List<MultipartFile> files`:
  - `MultipartFile`: Interface của Spring để xử lý uploaded file
  - `List<>`: Cho phép upload nhiều file cùng lúc
  - Frontend phải gửi với key name là "files"
  
- `@RequestParam(value = "title", required = false)`:
  - `required = false`: Optional parameter - không bắt buộc phải có
  - **Tại sao optional?** User có thể upload mà chưa đặt tên, hệ thống sẽ dùng tên file gốc
  
- `Authentication authentication`:
  - Spring Security tự động inject object này
  - Chứa thông tin user đã đăng nhập (từ JWT token)
  - **KHÔNG BAO GIỜ** tin tưởng userId từ request body - phải lấy từ Authentication
  
- `getCurrentUserId(authentication)`:
  - Extract UUID của user từ JWT token
  - **Bảo mật**: Đảm bảo document được tạo cho đúng người upload
  
- `ResponseEntity.status(HttpStatus.CREATED)`:
  - Trả về HTTP 201 Created thay vì 200 OK
  - **Best practice** cho RESTful API khi tạo resource mới

#### Endpoint 2: Get Documents by Owner

```java
@GetMapping
public ResponseEntity<List<DocumentResponse>> getDocuments(Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    return ResponseEntity.ok(documentService.getDocumentsByOwner(ownerId));
}
```

**Giải thích:**
- `@GetMapping`: GET /api/documents
- Lấy tất cả documents thuộc sở hữu của user hiện tại
- **Authorization**: User chỉ thấy documents của chính họ

#### Endpoint 3: Get Documents by Folder

```java
@GetMapping("/folder/{folderId}")
public ResponseEntity<List<DocumentResponse>> getDocumentsByFolder(
        @PathVariable UUID folderId,
        Authentication authentication) {

    UUID ownerId = getCurrentUserId(authentication);
    List<DocumentResponse> responses = documentService.getDocumentsByFolder(ownerId, folderId);
    return ResponseEntity.ok(responses);
}
```

**Giải thích:**
- `@PathVariable UUID folderId`: Lấy folderId từ URL path
- Service sẽ validate xem folder có thuộc về ownerId không
- **Tại sao cần ownerId?** Ngăn user xem documents trong folder của người khác

#### Endpoint 4: Delete Document (Soft Delete)

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    documentService.deleteDocument(id, ownerId);
    return ResponseEntity.noContent().build();
}
```

**Giải thích:**
- `ResponseEntity<Void>`: DELETE thành công không cần trả về body
- `noContent().build()`: HTTP 204 No Content - standard cho DELETE
- Logic thực tế: **Soft delete** - set `isDeleted = true` thay vì xóa khỏi DB

#### Endpoint 5: Restore from Trash

```java
@PostMapping("/{id}/restore")
public ResponseEntity<Void> restoreDocument(@PathVariable UUID id, Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    documentService.restoreDocument(id, ownerId);
    return ResponseEntity.ok().build();
}
```

**Giải thích:**
- POST thay vì PUT vì đây là một "action" không phải "update"
- Set `isDeleted = false` để khôi phục document

#### Method: getCurrentUserId() - QUAN TRỌNG

```java
private UUID getCurrentUserId(Authentication authentication) {
    if (authentication == null || authentication.getPrincipal() == null) {
        throw new RuntimeException("User chưa đăng nhập");
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof com.tugnw.aistudy.security.CustomUserDetails userDetails) {
        return userDetails.getAccount().getId();
    }

    throw new RuntimeException("Không thể xác định user");
}
```

**Giải thích:**
- `authentication.getPrincipal()`: Lấy object user từ security context
- `instanceof CustomUserDetails`: Type checking an toàn
- `userDetails.getAccount().getId()`: Extract UUID từ custom UserDetails
- **Tại sao quan trọng?** Mọi operations đều dựa vào method này để xác định ownership

---

### 2. DocumentServiceImpl.java - Logic Nghiệp Vụ

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final AccountRepository accountRepository;
    private final Cloudinary cloudinary;
```

**Dependencies:**
- `DocumentRepository`: JPA repository để truy vấn DB
- `Cloudinary cloudinary`: Bean đã config sẵn để upload file
- `@Slf4j`: Lombok tạo logger tự động

#### Method: uploadDocuments() - TRÁI TIM CỦA HỆ THỐNG

```java
@Override
@Transactional
public List<DocumentResponse> uploadDocuments(UUID ownerId, DocumentUploadRequest request) {
    Account owner = accountRepository.findById(ownerId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    Folder folder = null;
    if (request.getFolderId() != null) {
        folder = folderRepository.findById(request.getFolderId())
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        
        if (!folder.getOwner().getId().equals(ownerId)) {
            throw new SecurityException("Cannot upload to other user's folder");
        }
    }

    List<Document> savedDocuments = new ArrayList<>();

    for (MultipartFile file : request.getFiles()) {
        Document doc = new Document();
        doc.setTitle(request.getTitle() != null ? request.getTitle() : file.getOriginalFilename());
        doc.setDescription(request.getDescription());
        doc.setOwner(owner);
        doc.setFolder(folder);
        doc.setFileName(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setMimeType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        doc.setIsDeleted(false);

        Document savedDoc = documentRepository.save(doc);

        try {
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "resource_type", "auto",
                    "folder", "ai-study-hub/documents"
                )
            );

            savedDoc.setFileUrl(uploadResult.get("secure_url").toString());
            savedDoc.setCloudinaryPublicId(uploadResult.get("public_id").toString());
            savedDoc.setStatus(DocumentStatus.COMPLETED);
            
            savedDocuments.add(documentRepository.save(savedDoc));

        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", file.getOriginalFilename(), e);
            documentRepository.delete(savedDoc);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }

    return savedDocuments.stream()
            .map(DocumentMapper.INSTANCE::toResponse)
            .collect(Collectors.toList());
}
```

**Phân tích chi tiết:**

**1. Validate Owner:**
```java
Account owner = accountRepository.findById(ownerId)
        .orElseThrow(() -> new RuntimeException("User not found"));
```
- **Tại sao cần?** Đảm bảo ownerId thực sự tồn tại trong DB
- `orElseThrow()`: Nếu không tìm thấy → ném exception → HTTP 500

**2. Validate Folder Ownership:**
```java
if (!folder.getOwner().getId().equals(ownerId)) {
    throw new SecurityException("Cannot upload to other user's folder");
}
```
- **CỰC KỲ QUAN TRỌNG** cho bảo mật
- Ngăn user A upload vào folder của user B (ngay cả khi biết folderId)

**3. Tại sao lưu Document 2 lần?**

**Lần 1 (trước khi upload):**
```java
doc.setStatus(DocumentStatus.PENDING);
Document savedDoc = documentRepository.save(doc);
```
- Tạo record trong DB ngay lập tức
- **Lý do**: Có ID để tracking, ngay cả khi upload fail
- Status = PENDING cho biết đang trong quá trình upload

**Lần 2 (sau khi upload thành công):**
```java
savedDoc.setFileUrl(uploadResult.get("secure_url").toString());
savedDoc.setStatus(DocumentStatus.COMPLETED);
savedDocuments.add(documentRepository.save(savedDoc));
```
- Cập nhật URL và status sau khi có kết quả từ Cloudinary
- Status = COMPLETED xác nhận file đã sẵn sàng

**4. Cloudinary Upload:**
```java
Map uploadResult = cloudinary.uploader().upload(
    file.getBytes(),
    ObjectUtils.asMap(
        "resource_type", "auto",
        "folder", "ai-study-hub/documents"
    )
);
```

**Giải thích parameters:**
- `file.getBytes()`: Convert MultipartFile thành byte array
- `"resource_type", "auto"`: **QUAN TRỌNG**
  - Cloudinary tự động phát hiện loại file (image, PDF, DOCX, video...)
  - Tối ưu hóa storage và processing cho từng loại
- `"folder", "ai-study-hub/documents"`:
  - Tổ chức file trên Cloudinary theo structure
  - Dễ quản lý, backup, và cleanup

**5. Error Handling:**
```java
} catch (IOException e) {
    log.error("Failed to upload file to Cloudinary: {}", file.getOriginalFilename(), e);
    documentRepository.delete(savedDoc);
    throw new RuntimeException("Upload failed: " + e.getMessage());
}
```
- **Cleanup**: Nếu Cloudinary fail → xóa record trong DB
- **Tại sao?** Tránh "orphaned records" - record trong DB nhưng không có file thật
- Log error để debug

**6. Tại sao dùng @Transactional?**
- Nếu upload 5 files, file thứ 3 fail → rollback tất cả
- **Đảm bảo**: Hoặc tất cả thành công, hoặc không có gì thay đổi
- **Atomicity**: Nguyên tắc ACID của database

---

## 🎨 Frontend - Chi Tiết Code

### 1. documentApi.ts - API Service Layer

```typescript
export const documentApi = {
  upload: async (input: UploadDocumentRequest): Promise<Document[]> => {
    const fd = new FormData();
    fd.append("files", input.file);
    fd.append("title", input.title);
    if (input.description) {
      fd.append("description", input.description);
    }
    if (input.folderId) {
      fd.append("folderId", input.folderId);
    }
    return api<Document[]>("/api/documents", {
      method: "POST",
      formData: fd,
    });
  },
```

**Giải thích:**

**1. Tại sao dùng FormData?**
```typescript
const fd = new FormData();
```
- **Bắt buộc** khi upload file qua HTTP
- Browser tự động set `Content-Type: multipart/form-data`
- Cho phép gửi file + metadata trong cùng 1 request

**2. Append file và metadata:**
```typescript
fd.append("files", input.file);
fd.append("title", input.title);
```
- Key "files" phải khớp với `@RequestParam("files")` ở backend
- Có thể append nhiều files: `fd.append("files", file1); fd.append("files", file2);`

**3. Optional fields:**
```typescript
if (input.description) {
  fd.append("description", input.description);
}
```
- Chỉ append nếu có giá trị
- Backend có `required = false` nên không bắt buộc

---

## 🗄️ Database Schema

### Table: `document`

```sql
CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500),
    file_size BIGINT,
    mime_type VARCHAR(100),
    cloudinary_public_id VARCHAR(255),
    
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    folder_id UUID REFERENCES folder(id) ON DELETE SET NULL,
    
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    is_deleted BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_owner ON document(owner_id);
CREATE INDEX idx_document_folder ON document(folder_id);
CREATE INDEX idx_document_status ON document(status);
```

**Giải thích từng field:**

- `id UUID`: **Tại sao dùng UUID thay vì BIGINT?**
  - Không thể đoán: Ngăn enumerate attack (thử id=1,2,3...)
  - Globally unique: Có thể merge data từ nhiều DB
  - Security: User không biết có bao nhiêu documents trong hệ thống

- `cloudinary_public_id`: **Quan trọng** để xóa file trên Cloudinary
  - Khi xóa document → gọi `cloudinary.uploader().destroy(publicId)`
  
- `ON DELETE CASCADE`: Khi xóa owner → tự động xóa documents
  
- `ON DELETE SET NULL`: Khi xóa folder → documents vẫn còn nhưng không thuộc folder nào

- **Indexes**: 
  - `idx_document_owner`: Query "documents của user X" rất nhanh
  - `idx_document_folder`: Query "documents trong folder Y" rất nhanh

---

## 🤔 Lý Do Thiết Kế

### 1. Tại sao dùng Cloudinary thay vì lưu local?

**Ưu điểm:**
- ✅ Không giới hạn dung lượng (scale dễ dàng)
- ✅ CDN global: User ở bất kỳ đâu đều tải nhanh
- ✅ Backup tự động
- ✅ Không lo disk đầy
- ✅ Xử lý image/video tự động (resize, compress, thumbnail)

**Nhược điểm:**
- ❌ Chi phí (nhưng có free tier 10GB)
- ❌ Phụ thuộc third-party service

### 2. Tại sao Soft Delete?

**Hard Delete** (xóa vĩnh viễn):
```sql
DELETE FROM document WHERE id = ?;
```

**Soft Delete** (đánh dấu):
```sql
UPDATE document SET is_deleted = true WHERE id = ?;
```

**Lợi ích Soft Delete:**
- ✅ **Recovery**: User có thể khôi phục từ thùng rác
- ✅ **Audit trail**: Giữ lịch sử cho compliance
- ✅ **Safety**: Không mất data vĩnh viễn do lỗi code
- ✅ **Performance**: UPDATE nhanh hơn DELETE (không cần reindex)

**Trade-off:**
- ❌ DB size lớn hơn (nhưng có thể cleanup định kỳ)

### 3. Tại sao validate ownership ở mọi operation?

```java
if (!document.getOwner().getId().equals(ownerId)) {
    throw new SecurityException("Access denied");
}
```

**Lý do:**
- **Zero Trust Security**: Không tin JWT token là đủ
- **Defense in depth**: Nhiều lớp bảo mật
- **IDOR Prevention**: Ngăn Insecure Direct Object Reference
  - User A không thể thao tác document của User B bằng cách đoán ID

---

## ✅ Security Checklist

- ✅ **Authentication**: Tất cả endpoints cần đăng nhập (Spring Security)
- ✅ **Authorization**: Mọi operation đều validate ownership
- ✅ **Input Validation**: File size, mime type checks
- ✅ **SQL Injection**: Dùng JPA/Hibernate (parameterized queries)
- ✅ **UUID**: Ngăn enumeration attacks
- ⚠️ **File Upload**: Nên thêm virus scan (ClamAV)
- ⚠️ **Rate Limiting**: Ngăn upload spam (chưa implement)

---

**Tác giả**: AI Study Hub Team  
**Cập nhật**: 2026-07-02
