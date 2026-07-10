# Stabilization Audit Report

**Date**: 2026-07-10  
**Branch**: final/demo-v1  
**Scope**: Complete audit of architecture, API implementation, documentation, and data integrity

---

## 1. Architecture Documentation (Updated)

### Current Academic Hierarchy

```
Semester (1-9, BIGINT PK)
    │
    ▼
Subject (BIGINT PK, FK → Semester)
    │
    ▼
Folder (UUID PK, FK → Subject, FK → Account)
    │
    ▼
Document (UUID PK, FK → Folder, denormalized subject_id)
    │
    ├── AI Summary (stored in document.summary column)
    ├── Flashcard (FK → Document, multiple per doc)
    └── Quiz → Question (FK → Document, multiple per doc)
```

### Upload Flow

```
POST /api/documents (multipart/form-data)
  1. Receive MultipartFile[] + folderId (optional)
  2. Resolve owner from JWT
  3. Validate file type (PDF/DOCX/TXT/PPTX only)
  4. Validate file size ≤ 50MB
  5. Upload to Cloudinary (folder: ai-study-hub/documents)
  6. Save Document entity with status=COMPLETED
  7. Log activity (DOCUMENT_UPLOAD)
```

### AI Generation Flow

```
POST /api/ai/summary          → single documentId (SummaryRequest)
POST /api/flashcards/generate → single documentId (GenerateFlashcardsRequest)
POST /api/quizzes/generate    → single documentId (GenerateQuizRequest)

Each AI feature:
  1. Resolve document, verify ownership
  2. If force=false and existing content → return cached
  3. If force=true → delete existing, regenerate
  4. Extract text via RagService → prepareKnowledge (summary pipeline)
  5. Call Gemini API
  6. Parse JSON response → save to DB
```

---

## 2. API Implementation Audit

### Folder APIs

| Endpoint | Status | Issues |
|---|---|---|
| `POST /api/folder/create` | ✅ OK | subjectId required |
| `GET /api/folder/getall` | ✅ OK | Returns flat list (no hierarchy) |
| `GET /api/folder/getbyid/{id}` | ✅ OK | Owner/admin only |
| `PUT /api/folder/update/{id}` | ✅ OK | Name dedup check works |
| `DELETE /api/folder/delete/{id}` | ✅ OK | Soft delete (sets deletedAt) |
| `POST /api/folder/{id}/share` | ✅ OK | Via ShareService |
| `GET /api/folder/{id}/share-info` | ✅ OK | |

### Subject APIs

| Endpoint | Status | Issues |
|---|---|---|
| `GET /api/subjects/semester/{semesterId}` | ✅ OK | |
| `GET /api/subjects/{id}` | ✅ OK | |
| `POST /api/subjects` | ✅ Admin only | |
| `DELETE /api/subjects/{id}` | ✅ Admin only | Hard delete (not soft) |
| `PUT /api/subjects/{id}` | ❌ **MISSING** | No subject update endpoint |

### Document APIs

| Endpoint | Status | Issues |
|---|---|---|
| `POST /api/documents` | ✅ OK | Upload via multipart |
| `GET /api/documents` | ✅ OK | |
| `GET /api/documents/folder/{folderId}` | ⚠️ **No ownership check** | Missing owner validation |
| `GET /api/documents/shared/folder/{folderId}` | ✅ OK | Filters by status=READY |
| `GET /api/documents/shared` | ⚠️ **Stub** | Returns empty List.of() |
| `GET /api/documents/{id}` | ✅ OK | |
| `GET /api/documents/shared/{id}` | ✅ OK | |
| `PUT /api/documents/{id}` | ✅ OK | |
| `DELETE /api/documents/{id}` | ✅ OK | Soft delete |
| `POST /api/documents/{id}/restore` | ✅ OK | |
| `GET /api/documents/{id}/download` | ✅ OK | |
| `POST /api/documents/{id}/share` | ✅ OK | |
| `GET /api/documents/{id}/share-info` | ✅ OK | |
| `GET /api/documents/trash` | ✅ OK | |

### AI APIs

| Endpoint | Status | Issues |
|---|---|---|
| `POST /api/ai/summary` | ✅ OK | Single document only |
| `GET /api/ai/summary/{documentId}` | ✅ OK | Returns cached |
| `POST /api/flashcards/generate` | ✅ OK | Single document only |
| `GET /api/flashcards/{documentId}` | ✅ OK | |
| `POST /api/quizzes/generate` | ✅ OK | Single document only |
| `GET /api/quizzes/{documentId}` | ✅ OK | |

---

## 3. Findings: Issues & Discrepancies

### 🔴 Critical

#### F1. AI_GENERATION_API_CHANGES.md is aspirational — changes NOT implemented

`AI_GENERATION_API_CHANGES.md` documents `documentIds` (List<UUID>) for quiz/flashcard/summary endpoints, but actual code still uses single `UUID documentId` in:
- `GenerateQuizRequest.java` (line 15)
- `GenerateFlashcardsRequest.java` (line 13)
- `SummaryRequest.java` (line 21)
- `QuizController.java` (line 36)
- `FlashcardController.java` (line 36)

The doc describes a future state that was never coded.

#### F2. ShareService.saveToMyFolder uses wrong owner

`ShareServiceImpl.java:200`:
```java
UUID newOwnerId = share.getOwner().getId();
```
This sets the **original document owner** as the new document owner when saving to a folder. Should be the **current user (requester)** — the one who clicked "Save to my folder". The method signature doesn't even accept a requesterId parameter.

#### F3. Cloudinary secrets hardcoded in application.properties

```
cloudinary.api-key=899758863657928
cloudinary.api-secret=jdO40n0vqzqE3vhRPEcadx67QaQ
```

These should be environment variables, not plaintext in version control.

#### F4. DocumentUploadRequest @NotBlank on title conflicts with optional controller param

`DocumentUploadRequest.java` marks `title` as `@NotBlank`, but `DocumentController.java:46` only sets title if present:
```java
if (title != null) request.setTitle(title);
```
If title omitted, the DTO's `title` field is null, which fails `@NotBlank`. Though the controller doesn't use `@Valid` on the DTO (it builds it manually), so the constraint is silently not enforced. This is inconsistent and fragile.

### 🟠 High

#### F5. No Subject UPDATE endpoint

- `SubjectController.java`: No `PUT /api/subjects/{id}`
- `SubjectService.java`: No update method
- `SubjectServiceImpl.java`: No update implementation
- API.md claims "Create/Update Subject" pattern but no update exists for subjects

#### F6. No Subject UPDATE in API.md

API.md documents only POST (create) and DELETE — no update mentioned. But folder has update. Missing for both semester and subject.

#### F7. Semester full CRUD contradicts fixed 1-9 requirement

`SemesterController` exposes `POST /api/semesters` and `DELETE /api/semesters/{id}`. If semesters are fixed rows 1-9, there should be no create/delete endpoints. Recommend read-only + Flyway seed data.

#### F8. Folder entity lacks documented parent-child hierarchy

`document.md` and `folder.md` describe a self-referencing folder tree (`parentFolder`, `children`, `parent_folder_id`). The actual `Folder.java` entity has **none of these** — no parentFolder, no children, no description field. It only has: id, ownerId, name, aiSummary, subject, createdAt, updatedAt, deletedAt. The old parent-child folder model was replaced by the Subject→Folder hierarchy but docs were not updated.

#### F9. Document entity differs from documented schema

`document.md` describes:
- `is_deleted` boolean with `@SQLDelete`/`@Where` annotations
- `file_name` field
- JPA `@ManyToOne` relationship to Account

Actual `Document.java`:
- No `isDeleted` — uses `deletedAt` timestamp for soft delete
- No `fileName` field
- `ownerId` is plain `UUID` field, not `@ManyToOne`
- No `@SQLDelete` or `@Where` — all queries explicitly filter `deletedAt` in repository methods

#### F10. getDocumentsByFolder lacks ownership check

`DocumentServiceImpl.java:136-143`:
```java
public List<DocumentResponse> getDocumentsByFolder(UUID ownerId, UUID folderId) {
    // TODO: Check owner has permission to access this folder
    List<Document> documents = documentRepository
        .findByFolderIdAndDeletedAtIsNullOrderByCreatedAtDesc(folderId);
    return documents.stream()...
}
```
The TODO is still there — no ownership/permission validation. User can pass any folderId and see its documents.

#### F11. KnowledgePreparationService.text Truncation

`FlashcardServiceImpl.java:101`: Content truncated to `Math.min(3000, documentText.length())` before AI call.
`QuizServiceImpl.java:157`: Content truncated to `Math.min(5000, documentText.length())`.
These differ arbitrarily and lose content for long documents.

### 🟡 Medium

#### F12. FolderResponse.documentCount always 0

`FolderMapper.java:13`: `@Mapping(target = "documentCount", ignore = true)` — the count field is never populated. Service doesn't count documents per folder.

#### F13. V1 schema out of sync with actual entity state

- V1 has `username VARCHAR(10)` → V1.2 fixes to `VARCHAR(50)`
- V1 has no `subject_id` in folder → V10 adds it
- V1 has no `plan` or `storage_gb` in account → V6, V7 add them
- V1 init schema does not represent current database shape

#### F14. Shared document filtering by status="READY" incompatible with upload flow

`DocumentServiceImpl.getSharedFolderDocuments` (line 155): filters by status `"READY"`, but documents are uploaded with status `"COMPLETED"` (DocumentServiceImpl.java:98). This means uploaded documents never appear in shared folder views. The KnowledgePreparationService also uses `"READY"` status.

#### F15. FlashcardServiceImpl and QuizServiceImpl use raw System.out.println for logging

Should use `@Slf4j` logger like other services.

#### F16. No ShareService interface for saveToMyFolder

`ShareService.java` doesn't declare `saveToMyFolder` — it's only in `ShareServiceImpl.java`.

#### F17. ShareRequest DTO uses "email" + "username" but ShareController only delegates to shareFolder via POST /api/shares

The controller `/api/shares` always calls `shareService.shareFolder()` — there's no dynamic dispatch based on whether folderId or documentId is set. The routing happens inside the service methods which check individually.

#### F18. V1 schema defines a CHECK constraint on correct_answer only, but none on quiz_answer.selected_answer

`question.correct_answer` has `CHECK (correct_answer IN ('A','B','C','D'))`.  
`quiz_answer.selected_answer` also has the constraint: `CHECK (selected_answer IN ('A','B','C','D'))`. Actually looking at V1 it does have it. This is fine.

---

## 4. Semester Requirements Analysis

**Current state**: Full CRUD on semesters via `SemesterController` (POST/DELETE for ADMIN).

**Recommendation**: Since semesters 1-9 are fixed:
1. Replace POST/DELETE with read-only `GET /api/semesters` and `GET /api/semesters/{id}`
2. Seed semesters 1-9 via Flyway migration
3. Remove `SemesterServiceImpl.createSemester()` and `deleteSemester()`
4. Remove `@PreAuthorize` annotations from controller (no longer needed)

**Do NOT implement until confirmed.**

---

## 5. End-to-End Verification Checklist

### Upload → Open Document → Generate Summary → Generate Flashcards → Generate Quiz

| Step | Action | Expected | Actual |
|---|---|---|---|
| 1 | Register a user | 200 with JWT | ✅ |
| 2 | Login | 200 with JWT | ✅ |
| 3 | GET /api/semesters | List semesters | ✅ |
| 4 | POST /api/subjects | Subject created under semester | ✅ |
| 5 | POST /api/folder/create | Folder under subject | ✅ |
| 6 | Upload PDF via `POST /api/documents` | Document with COMPLETED status | ✅ |
| 7 | GET /api/documents/{id} | Document metadata | ✅ |
| 8 | POST /api/ai/summary (force=false) | Summary generated or cached | ✅ |
| 9 | GET /api/ai/summary/{id} | Cached summary | ✅ |
| 10 | POST /api/flashcards/generate (force=false) | Flashcards from doc | ✅ |
| 11 | GET /api/flashcards/{documentId} | Flashcards list | ✅ |
| 12 | POST /api/quizzes/generate (force=false) | Quiz with questions | ✅ |
| 13 | GET /api/quizzes/{documentId} | Quiz list | ✅ |
| 14 | POST with force=true | Regenerates content | ✅ |
| 15 | Share document via `POST /api/documents/{id}/share` | Share created | ✅ |
| 16 | GET /api/shares/shared-with-me | Shared item visible | ❓ Depends on status=READY (F14) |
| 17 | Save shared doc via `POST /api/shares/{id}/save` | Copied to user folder | 🔴 **BUG**: saves under original owner (F2) |
| 18 | DELETE /api/documents/{id} | Soft-delete → appears in trash | ✅ |
| 19 | POST /api/documents/{id}/restore | Restored from trash | ✅ |

### Known Blockers

1. **F14**: Shared folder documents filtered by `status="READY"` — uploaded docs have `status="COMPLETED"`. Will not appear in shared views.
2. **F2**: Save to my folder assigns document to original owner, not the recipient.
3. **F10**: `GET /api/documents/folder/{folderId}` — no ownership check, anyone can list documents by folderId.

---

## 6. Required Fixes Summary

### Documentation
- [ ] Update API.md: remove multi-doc AI claims until implemented, add subject update endpoint
- [ ] Update document.md: remove parent-child folder hierarchy, reflect actual Folder entity
- [ ] Update folder.md: remove self-referencing, reflect subject-based hierarchy
- [ ] Update AI_GENERATION_API_CHANGES.md: mark as NOT IMPLEMENTED or implement it
- [ ] Add missing shared endpoint docs (`save`, `deleteByToken`)

### Code Fixes (Priority Order)

1. 🔴 **F3**: Move Cloudinary secrets to env vars
2. 🔴 **F2**: Fix `saveToMyFolder` — pass requesterId, use as newOwnerId
3. 🔴 **F1**: Align AI_GENERATION_API_CHANGES.md with actual code (remove multi-doc claims) OR implement multi-doc support
4. 🟠 **F5**: Implement `PUT /api/subjects/{id}` endpoint
5. 🟠 **F8**: Delete obsolete folder documentation, update to current model
6. 🟠 **F9**: Update document.md to match actual entity
7. 🟠 **F10**: Implement ownership check in `getDocumentsByFolder`
8. 🟠 **F11**: Fix content length truncation (use consistent strategy)
9. 🟡 **F12**: Populate `documentCount` in FolderResponse
10. 🟡 **F14**: Align status constants — either use "COMPLETED" everywhere or "READY" everywhere
11. 🟡 **F15**: Replace `System.out.println` with SLF4J logger

### Semester Decision
- [ ] After confirmation: convert Semester to read-only + Flyway seed data

---

## 7. Documentation Files Delivered

| File | Purpose |
|---|---|
| `STABILIZATION_AUDIT.md` | This report |
| `API.md` | Update required (see Section 6) |
| `document.md` | Update required |
| `folder.md` | Update required |
| `AI_GENERATION_API_CHANGES.md` | Remove or implement |

---

**End of Audit Report**
