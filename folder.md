# Tài Liệu Hệ Thống Quản Lý Folder

## 📋 Mục Lục
1. [Tổng Quan Hệ Thống](#tổng-quan-hệ-thống)
2. [Kiến Trúc & Cấu Trúc Dữ Liệu](#kiến-trúc--cấu-trúc-dữ-liệu)
3. [Backend - Chi Tiết Code](#backend---chi-tiết-code)
4. [Frontend - Chi Tiết Code](#frontend---chi-tiết-code)
5. [Database Schema](#database-schema)
6. [Lý Do Thiết Kế](#lý-do-thiết-kế)

---

## 🎯 Tổng Quan Hệ Thống

Module Folder cung cấp **cấu trúc phân cấp** để người dùng tổ chức tài liệu, tương tự như hệ thống thư mục trên Windows/macOS.

### Các Tính Năng Chính:
- ✅ Tạo folder mới (có thể nested - lồng nhau)
- ✅ Đọc danh sách folders của user
- ✅ Cập nhật tên/mô tả folder
- ✅ Xóa folder (soft delete)
- ✅ Parent-child relationship (folder cha-con)
- ✅ Move documents vào folder
- ✅ Share folder với người khác
- ✅ Ownership validation nghiêm ngặt

### Công Nghệ Sử Dụng:
- **Backend**: Spring Boot, JPA/Hibernate, PostgreSQL
- **Frontend**: React, TypeScript
- **Pattern**: Self-referencing relationship (quan hệ tự tham chiếu)

---

## 🏗️ Kiến Trúc & Cấu Trúc Dữ Liệu

### Cấu Trúc Folder Hierarchy:

```
My Documents (root folder, parent = null)
├── 📁 Work Projects (parent = My Documents)
│   ├── 📁 Project A (parent = Work Projects)
│   │   ├── 📄 design.pdf
│   │   └── 📄 requirements.docx
│   └── 📁 Project B (parent = Work Projects)
│       └── 📄 proposal.pptx
├── 📁 Personal (parent = My Documents)
│   ├── 📄 resume.pdf
│   └── 📄 certificates.zip
└── 📁 Study Materials (parent = My Documents)
    └── 📄 notes.txt
```

**Đặc điểm:**
- Mỗi folder có thể có **1 parent** hoặc **null** (root folder)
- Mỗi folder có thể có **nhiều children** (sub-folders)
- Depth không giới hạn (có thể lồng nhiều cấp)

### Database Relationship:

```
┌─────────────────┐
│     Folder      │
├─────────────────┤
│ id (UUID)       │
│ name            │
│ owner_id  ──────┼─→ Account
│ parent_id ──────┼─→ Folder (self-reference)
│ is_deleted      │
└─────────────────┘
      ↑
      │ (many folders can reference one parent)
      │
   children
```

**Self-referencing relationship:**
- `parent_folder_id` → `folder.id`
- Một folder trỏ đến folder cha của nó
- Query để lấy children: `SELECT * FROM folder WHERE parent_folder_id = ?`

---

## 💻 Backend - Chi Tiết Code

### 1. FolderController.java

**Vai trò**: REST API controller cho folder operations

```java
@CrossOrigin(origins = "*", allowedHeaders = "*", exposedHeaders = "Authorization")
@RestController
@RequestMapping("/api/folder")
@Validated
public class FolderController {
    private final FolderService folderService;
    private final ShareService shareService;
```

**Annotations:**
- `@CrossOrigin(*)`: Cho phép CORS - **Production nên giới hạn origin cụ thể**
- `@RequestMapping("/api/folder")`: Base path - tất cả endpoints bắt đầu với `/api/folder`
- `@Validated`: Enable validation cho DTOs

#### Endpoint 1: Create Folder

```java
@PostMapping("/create")
public ResponseEntity<FolderResponse> createFolder(
        @RequestBody @Valid FolderCreateRequest request,
        Authentication authentication) {

    UUID ownerId = getCurrentUserId(authentication);
    FolderResponse response = folderService.createFolder(ownerId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

**Giải thích:**
- `@Valid`: Trigger validation trên `FolderCreateRequest`
  - Ví dụ: `@NotBlank String name` → name không được rỗng
- `getCurrentUserId(authentication)`: Lấy UUID từ JWT token
- `HttpStatus.CREATED` (201): Best practice cho resource creation

**FolderCreateRequest DTO:**
```java
public class FolderCreateRequest {
    @NotBlank(message = "Folder name is required")
    private String name;
    
    private String description;
    
    private UUID parentFolderId; // Nullable - nếu null thì là root folder
}
```

#### Endpoint 2: Get All Folders (của user)

```java
@GetMapping("/getall")
public ResponseEntity<List<FolderResponse>> getFolders(Authentication authentication) {
    UUID ownerId = getCurrentUserId(authentication);
    List<FolderResponse> responses = folderService.getFoldersByOwner(ownerId);
    return ResponseEntity.ok(responses);
}
```

**Giải thích:**
- Endpoint này trả về **tất cả folders** của user
- **Không phân cấp** - frontend tự xây dựng tree structure
- **Tại sao không trả tree structure từ backend?**
  - Performance: Tránh recursive queries phức tạp
  - Flexibility: Frontend có thể render theo nhiều cách (list, tree, grid...)
  - Simplicity: Backend chỉ cần một query đơn giản

#### Endpoint 3: Get Folder By ID

```java
@GetMapping("/getbyid/{id}")
public ResponseEntity<FolderResponse> getFolderById(
        @PathVariable UUID id,
        Authentication authentication) {

    UUID ownerId = getCurrentUserId(authentication);
    FolderResponse response = folderService.getFolderById(id, ownerId);
    return ResponseEntity.ok(response);
}
```

**Giải thích:**
- `@PathVariable UUID id`: Extract id từ URL path
- Service sẽ validate ownership: `folder.owner.id == ownerId`
- **Bảo mật**: User không thể xem folder của người khác

#### Endpoint 4: Update Folder

```java
@PutMapping("/update/{id}")
public ResponseEntity<FolderResponse> updateFolder(
        @PathVariable UUID id,
        @RequestBody FolderUpdateRequest request,
        Authentication authentication) {

    UUID ownerId = getCurrentUserId(authentication);
    FolderResponse response = folderService.updateFolder(id, ownerId, request);
    return ResponseEntity.ok(response);
}
```

**FolderUpdateRequest:**
```java
public class FolderUpdateRequest {
    private String name;
    private String description;
    private UUID parentFolderId; // Có thể move folder sang parent khác
}
```

**Lưu ý:**
- Có thể đổi `parentFolderId` → **move folder**
- Service phải validate: không được move folder vào chính nó (circular reference)

#### Endpoint 5: Delete Folder (Soft Delete)

```java
@DeleteMapping("/delete/{id}")
public ResponseEntity<Void> deleteFolder(
        @PathVariable UUID id,
        Authentication authentication) {

    UUID ownerId = getCurrentUserId(authentication);
    folderService.deleteFolder(id, ownerId);
    return ResponseEntity.noContent().build();
}
```

**Giải thích:**
- `ResponseEntity<Void>`: DELETE không cần body
- `noContent()`: HTTP 204 No Content
- Logic: **Soft delete** - set `is_deleted = true`

---

### 2. FolderServiceImpl.java - Logic Nghiệp Vụ

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderServiceImpl implements FolderService {
    private final FolderRepository folderRepository;
    private final AccountRepository accountRepository;
```

#### Method: createFolder()

```java
@Override
@Transactional
public FolderResponse createFolder(UUID ownerId, FolderCreateRequest request) {
    // 1. Validate owner exists
    Account owner = accountRepository.findById(ownerId)
            .orElseThrow(() -> new RuntimeException("User not found"));

    Folder folder = new Folder();
    folder.setName(request.getName());
    folder.setDescription(request.getDescription());
    folder.setOwner(owner);
    folder.setIsDeleted(false);

    // 2. Handle parent folder
    if (request.getParentFolderId() != null) {
        Folder parentFolder = folderRepository.findById(request.getParentFolderId())
                .orElseThrow(() -> new RuntimeException("Parent folder not found"));

        // **KIỂM TRA BẢO MẬT QUAN TRỌNG**
        if (!parentFolder.getOwner().getId().equals(ownerId)) {
            throw new SecurityException("Cannot create subfolder in another user's folder");
        }

        folder.setParentFolder(parentFolder);
    }

    Folder savedFolder = folderRepository.save(folder);
    return FolderMapper.INSTANCE.toResponse(savedFolder);
}
```

**Phân tích chi tiết:**

**1. Validate owner exists:**
```java
Account owner = accountRepository.findById(ownerId)
        .orElseThrow(() -> new RuntimeException("User not found"));
```
- Đảm bảo ownerId hợp lệ
- `orElseThrow()`: Ném exception nếu không tìm thấy → HTTP 500

**2. Kiểm tra ownership của parent folder:**
```java
if (!parentFolder.getOwner().getId().equals(ownerId)) {
    throw new SecurityException("Cannot create subfolder in another user's folder");
}
```
- **CỰC KỲ QUAN TRỌNG** cho security
- **Ngăn chặn attack:** User A không thể tạo subfolder trong folder của User B
- **Kịch bản tấn công:**
  1. User A đoán được folderId của User B (ví dụ: UUID leak qua URL)
  2. User A gửi request tạo folder với `parentFolderId = folder_cua_B`
  3. Nếu không có check này → folder được tạo thành công → User A có quyền trong folder của User B

**3. Set parent relationship:**
```java
folder.setParentFolder(parentFolder);
```
- JPA tự động quản lý foreign key `parent_folder_id`
- Không cần manually set `parentFolder.getChildren().add(folder)`

**4. Tại sao @Transactional?**
- Nếu `save()` fail → rollback tất cả
- Đảm bảo atomicity

---

#### Method: getFoldersByOwner()

```java
@Override
public List<FolderResponse> getFoldersByOwner(UUID ownerId) {
    List<Folder> folders = folderRepository.findByOwnerIdAndIsDeletedFalse(ownerId);
    return folders.stream()
            .map(FolderMapper.INSTANCE::toResponse)
            .collect(Collectors.toList());
}
```

**Giải thích:**

**Repository method:**
```java
List<Folder> findByOwnerIdAndIsDeletedFalse(UUID ownerId);
```
- Spring Data JPA tự động generate query:
  ```sql
  SELECT * FROM folder 
  WHERE owner_id = ? AND is_deleted = false
  ```
- **Không dùng recursive query** → trả flat list, frontend tự build tree

**Mapping to DTO:**
```java
.map(FolderMapper.INSTANCE::toResponse)
```
- Dùng MapStruct để convert Entity → DTO
- **Tại sao cần DTO?**
  - Không expose entity trực tiếp (security)
  - Kiểm soát dữ liệu trả về (chỉ trả fields cần thiết)
  - Tránh circular reference (folder → parent → children → parent...)

---

#### Method: deleteFolder()

```java
@Override
@Transactional
public void deleteFolder(UUID folderId, UUID ownerId) {
    Folder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new RuntimeException("Folder not found"));

    // Validate ownership
    if (!folder.getOwner().getId().equals(ownerId)) {
        throw new SecurityException("Access denied");
    }

    // Soft delete
    folder.setIsDeleted(true);
    folderRepository.save(folder);

    // TODO: Xử lý children và documents bên trong
    // Option 1: Xóa cascade (recursive soft delete)
    // Option 2: Chỉ cho xóa folder rỗng
    // Option 3: Move children lên parent folder
}
```

**Thảo luận về Logic Xóa:**

**Vấn đề:** Khi xóa folder, phải quyết định xử lý gì với:
1. **Sub-folders** (children)
2. **Documents** trong folder

**Các phương án:**

**Option 1: Cascading Soft Delete (Hiện tại chưa implement)**
```java
private void softDeleteRecursive(Folder folder) {
    folder.setIsDeleted(true);
    
    // Xóa tất cả documents trong folder
    for (Document doc : folder.getDocuments()) {
        doc.setIsDeleted(true);
    }
    
    // Đệ quy xóa tất cả subfolders
    for (Folder child : folder.getChildren()) {
        softDeleteRecursive(child);
    }
    
    folderRepository.save(folder);
}
```
**Ưu điểm:**
- UX tốt: Xóa nhanh
- Giống Windows/macOS

**Nhược điểm:**
- Nguy hiểm: Có thể xóa nhiều dữ liệu không mong muốn
- Performance: Recursive query chậm với folder sâu

**Option 2: Chỉ cho xóa folder rỗng**
```java
if (!folder.getChildren().isEmpty() || !folder.getDocuments().isEmpty()) {
    throw new IllegalStateException("Cannot delete non-empty folder");
}
```
**Ưu điểm:**
- An toàn nhất
- Bắt user dọn dẹp trước khi xóa

**Nhược điểm:**
- UX kém: Phải xóa nhiều lần

**Option 3: Move children lên parent**
```java
if (folder.getParentFolder() != null) {
    for (Folder child : folder.getChildren()) {
        child.setParentFolder(folder.getParentFolder());
    }
    for (Document doc : folder.getDocuments()) {
        doc.setFolder(folder.getParentFolder());
    }
}
```
**Ưu điểm:**
- Không mất dữ liệu
- Flexible

**Nhược điểm:**
- Có thể gây confusion cho user

**Khuyến nghị:** Implement **Option 1** với confirmation dialog ở frontend.

---

### 3. Folder Entity

```java
@Entity
@Table(name = "folder")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Account owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder; // Self-reference

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL)
    private List<Folder> children = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL)
    private List<Document> documents = new ArrayList<>();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
```

**Giải thích từng field:**

**1. Self-referencing relationship:**
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_folder_id")
private Folder parentFolder;

@OneToMany(mappedBy = "parentFolder")
private List<Folder> children;
```
- `parentFolder`: Trỏ đến folder cha
- `children`: Danh sách folders con
- **Quan hệ đệ quy**: Folder → Folder

**2. Tại sao FetchType.LAZY?**
```java
fetch = FetchType.LAZY
```
- **Ngăn chặn N+1 problem**
- **Tránh StackOverflowError:**
  - Nếu dùng `EAGER`: Load folder → load parent → load parent.parent → ... → infinite loop
- Chỉ load khi explicitly gọi `folder.getParentFolder()`

**3. CascadeType.ALL:**
```java
cascade = CascadeType.ALL
```
- **Lưu ý:** Nếu dùng với `children`, xóa folder sẽ xóa tất cả subfolders
- **Nguy hiểm** với soft delete - nên remove cascade hoặc dùng `CascadeType.PERSIST`

**4. Timestamps:**
```java
@CreationTimestamp
private Instant createdAt;

@UpdateTimestamp
private Instant updatedAt;
```
- Hibernate tự động set
- `Instant` thay vì `LocalDateTime`: UTC timezone-safe

---

## 🎨 Frontend - Chi Tiết Code

### Build Tree Structure từ Flat List

Backend trả về flat list, frontend phải build tree:

```typescript
interface Folder {
  id: string;
  name: string;
  parentFolderId: string | null;
  children?: Folder[];
}

function buildFolderTree(folders: Folder[]): Folder[] {
  const folderMap = new Map<string, Folder>();
  const rootFolders: Folder[] = [];

  // 1. Tạo map để lookup nhanh
  folders.forEach(folder => {
    folderMap.set(folder.id, { ...folder, children: [] });
  });

  // 2. Build parent-child relationships
  folders.forEach(folder => {
    const node = folderMap.get(folder.id)!;
    
    if (folder.parentFolderId === null) {
      // Root folder
      rootFolders.push(node);
    } else {
      // Add to parent's children
      const parent = folderMap.get(folder.parentFolderId);
      if (parent) {
        parent.children!.push(node);
      }
    }
  });

  return rootFolders;
}
```

**Giải thích:**
- **Complexity:** O(n) - chỉ duyệt 2 lần
- **Map lookup:** O(1) thay vì O(n) với `find()`
- **Result:** Tree structure để render TreeView component

---

## 🗄️ Database Schema

```sql
CREATE TABLE folder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    parent_folder_id UUID REFERENCES folder(id) ON DELETE CASCADE,
    
    is_deleted BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_folder_owner ON folder(owner_id);
CREATE INDEX idx_folder_parent ON folder(parent_folder_id);
CREATE INDEX idx_folder_deleted ON folder(is_deleted);
```

**Giải thích:**

**1. Self-referencing foreign key:**
```sql
parent_folder_id UUID REFERENCES folder(id)
```
- Trỏ đến chính bảng `folder`
- **NULL** = root folder

**2. ON DELETE CASCADE:**
```sql
ON DELETE CASCADE
```
- Khi xóa owner → xóa tất cả folders của owner
- Khi xóa parent folder → xóa tất cả subfolders
- **Lưu ý:** Với soft delete, không nên dùng CASCADE

**3. Indexes:**
- `idx_folder_owner`: Query "folders của user X"
- `idx_folder_parent`: Query "subfolders của folder Y"
- `idx_folder_deleted`: Filter `is_deleted = false`

---

## 🤔 Lý Do Thiết Kế

### 1. Tại sao dùng Self-Referencing thay vì Separate Table?

**Phương án 1: Self-referencing (Đang dùng)**
```sql
folder (id, name, parent_folder_id)
```

**Phương án 2: Separate hierarchy table**
```sql
folder (id, name)
folder_hierarchy (parent_id, child_id, depth)
```

**Ưu điểm Self-referencing:**
- ✅ Đơn giản: Chỉ 1 table
- ✅ Trực quan: Dễ hiểu
- ✅ Dễ query 1 level: `WHERE parent_id = ?`

**Nhược điểm:**
- ❌ Query toàn bộ subtree phức tạp (cần recursive CTE)
- ❌ Không track depth

**Khi nào dùng separate table?**
- Cần query "tất cả descendants" thường xuyên
- Cần biết depth của mỗi node
- Tree rất sâu (> 10 levels)

### 2. Tại sao Flat List thay vì Nested JSON?

**Backend trả Flat:**
```json
[
  {"id": "1", "name": "A", "parentId": null},
  {"id": "2", "name": "B", "parentId": "1"},
  {"id": "3", "name": "C", "parentId": "1"}
]
```

**Backend trả Nested:**
```json
[
  {
    "id": "1",
    "name": "A",
    "children": [
      {"id": "2", "name": "B"},
      {"id": "3", "name": "C"}
    ]
  }
]
```

**Lợi ích Flat:**
- ✅ **Performance:** Không cần recursive query
- ✅ **Flexible:** Frontend có thể render theo nhiều cách
- ✅ **Simple:** Backend chỉ cần `findByOwnerId()`

**Nhược điểm:**
- ❌ Frontend phải tự build tree (nhưng O(n) nên không vấn đề)

---

## ✅ Security Checklist

- ✅ **Ownership validation:** Mọi operation đều check `folder.owner.id == ownerId`
- ✅ **Parent ownership check:** Không thể tạo subfolder trong folder của người khác
- ✅ **UUID:** Ngăn enumeration attack
- ✅ **Soft delete:** Recovery từ thùng rác
- ⚠️ **Circular reference prevention:** Chưa implement (user có thể move folder A vào subfolder của A)
- ⚠️ **Depth limit:** Chưa giới hạn (user có thể tạo folder sâu vô hạn → performance issue)

**TODO:**
```java
// Prevent circular reference
if (isDescendantOf(folder, newParent)) {
    throw new IllegalArgumentException("Cannot move folder into its own subfolder");
}

// Limit depth
if (calculateDepth(newParent) >= MAX_DEPTH) {
    throw new IllegalArgumentException("Maximum folder depth exceeded");
}
```

---

**Tác giả**: AI Study Hub Team  
**Cập nhật**: 2026-07-02
