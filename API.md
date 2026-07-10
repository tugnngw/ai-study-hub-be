# AI Study Hub — API Reference

## Academic Hierarchy

```
Semester → Subject → Folder → Document → AI Features (Summary / Flashcards / Quiz)
```

Upload flow: pick Semester → pick Subject → pick Folder → upload file.

---

## Authentication

Base: `POST /api/auth`

### Register

```
POST /api/auth/register
```

```json
{
  "username": "john_doe",
  "password": "SecurePass123",
  "fullName": "John Doe"
}
```

**Response** `200`:
```json
{
  "code": 200,
  "message": "Registered",
  "data": {
    "userId": "uuid",
    "username": "john_doe",
    "email": "john@example.com",
    "fullName": "John Doe",
    "role": "USER",
    "accessToken": "jwt...",
    "refreshToken": null,
    "expiresIn": 900000
  }
}
```

### Login

```
POST /api/auth/login
```

```json
{
  "username": "john_doe",
  "password": "SecurePass123"
}
```

**Response** `200`: Same structure as register.

### Refresh Token

```
POST /api/auth/refresh
```

```json
{
  "refreshToken": "jwt..."
}
```

### Logout

```
POST /api/auth/logout
```

---

## Account

### Get Current User

```
GET /api/account/me
```

Headers: `Authorization: Bearer <token>`

**Response** `200`:
```json
{
  "code": 200,
  "message": "User retrieved successfully",
  "data": {
    "id": "uuid",
    "username": "john_doe",
    "email": "john@example.com",
    "fullName": "John Doe",
    "avatarUrl": null,
    "role": "USER",
    "status": "ACTIVE",
    "plan": "FREE",
    "storageGb": 1,
    "createdAt": "2025-01-01T00:00:00Z",
    "updatedAt": "2025-01-01T00:00:00Z"
  }
}
```

---

## Semesters

### List All Semesters

```
GET /api/semesters
```

**Response** `200`:
```json
[
  { "id": 1, "name": "Spring 2025", "startDate": "2025-01-15", "endDate": "2025-05-20" }
]
```

### Get Semester by ID

```
GET /api/semesters/{id}
```

### Create Semester (Admin)

```
POST /api/semesters
```

```json
{
  "name": "Fall 2025",
  "startDate": "2025-09-01",
  "endDate": "2025-12-20"
}
```

### Delete Semester (Admin)

```
DELETE /api/semesters/{id}
```

---

## Subjects

### List Subjects by Semester

```
GET /api/subjects/semester/{semesterId}
```

**Response** `200`:
```json
[
  { "id": 1, "semesterId": 1, "code": "SWP391", "name": "Software Development Project" }
]
```

### Get Subject by ID

```
GET /api/subjects/{id}
```

### Create Subject (Admin)

```
POST /api/subjects
```

```json
{
  "semesterId": 1,
  "code": "SWP391",
  "name": "Software Development Project"
}
```

### Delete Subject (Admin)

```
DELETE /api/subjects/{id}
```

---

## Folders

### Create Folder

```
POST /api/folder/create
```

```json
{
  "name": "Chapter 1: Introduction",
  "subjectId": 1
}
```

**Response** `201`:
```json
{
  "id": "uuid",
  "name": "Chapter 1: Introduction",
  "aiSummary": null,
  "subjectId": 1,
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:00",
  "documentCount": 0
}
```

### List User's Folders

```
GET /api/folder/getall
```

### Get Folder by ID

```
GET /api/folder/getbyid/{id}
```

### Update Folder

```
PUT /api/folder/update/{id}
```

```json
{
  "name": "Updated Name",
  "subjectId": 2
}
```

### Delete Folder (Soft)

```
DELETE /api/folder/delete/{id}
```

### Share Folder

```
POST /api/folder/{id}/share
```

```json
{
  "email": "friend@example.com",
  "visibility": "private"
}
```

### Get Folder Share Info

```
GET /api/folder/{id}/share-info
```

---

## Documents

### Upload Documents

```
POST /api/documents
Content-Type: multipart/form-data
```

| Field | Type | Required |
|-------|------|----------|
| files | MultipartFile[] | Yes |
| title | String | No |
| description | String | No |
| folderId | UUID | No |

**Response** `201`:
```json
[
  {
    "id": "uuid",
    "ownerId": "uuid",
    "folderId": "uuid",
    "subjectId": 1,
    "title": "Java Concurrency Notes",
    "description": null,
    "summary": null,
    "status": "COMPLETED",
    "mimeType": "application/pdf",
    "fileSize": 204800,
    "cloudinaryUrl": "https://res.cloudinary.com/...",
    "createdAt": "2025-01-15T10:00:00",
    "deletedAt": null
  }
]
```

### List User's Documents

```
GET /api/documents
```

### List Documents by Folder

```
GET /api/documents/folder/{folderId}
```

### List Shared Folder Documents

```
GET /api/documents/shared/folder/{folderId}
```

### Get Document by ID

```
GET /api/documents/{id}
```

### Get Shared Document by ID

```
GET /api/documents/shared/{id}
```

### Update Document

```
PUT /api/documents/{id}
```

```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "folderId": "uuid"
}
```

### Delete Document (Soft)

```
DELETE /api/documents/{id}
```

### Restore from Trash

```
POST /api/documents/{id}/restore
```

### Get Download URL

```
GET /api/documents/{id}/download
```

### Share Document

```
POST /api/documents/{id}/share
```

```json
{
  "email": "friend@example.com",
  "visibility": "private"
}
```

### Get Document Share Info

```
GET /api/documents/{id}/share-info
```

### Trash Documents

```
GET /api/documents/trash
```

---

## AI Summary

### Generate or Regenerate Summary

```
POST /api/ai/summary
```

```json
{
  "documentId": "uuid",
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID (required) | — | Document to summarize |
| force | boolean | false | `false` → return cached if exists; `true` → regenerate and overwrite cache |

**Response** `200`:
```json
{
  "code": 200,
  "message": "AI Summary generated successfully",
  "data": {
    "markdown": "# Key Concepts\n\n..."
  }
}
```

### Get Cached Summary (Read-only)

```
GET /api/ai/summary/{documentId}
```

Returns cached summary without generating. Returns empty `markdown` if none exists.

---

## Flashcards

### Generate Flashcards

```
POST /api/flashcards/generate
```

```json
{
  "documentId": "uuid",
  "numberOfCards": 10,
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID (required) | — | Document to generate from |
| numberOfCards | integer (min: 1) | — | Number of flashcards |
| force | boolean | false | `false` → return existing; `true` → delete old, generate new |

**Response** `201`:
```json
[
  {
    "id": 1,
    "frontContent": "What is polymorphism?",
    "backContent": "The ability of objects to take multiple forms...",
    "generatedByAi": true,
    "createdAt": "2025-01-15T10:00:00"
  }
]
```

### Get Flashcards by Document

```
GET /api/flashcards/{documentId}
```

---

## Quizzes

### Generate Quiz

```
POST /api/quizzes/generate
```

```json
{
  "documentId": "uuid",
  "numberOfQuestions": 5,
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID (required) | — | Document to generate from |
| numberOfQuestions | integer (min: 1) | — | Number of questions |
| force | boolean | false | `false` → return existing; `true` → delete old, generate new |

**Response** `201`:
```json
{
  "id": 1,
  "title": "AI-Generated Quiz",
  "generatedByAi": true,
  "createdAt": "2025-01-15T10:00:00",
  "questions": [
    {
      "id": 1,
      "content": "What is the time complexity of binary search?",
      "optionA": "O(n)",
      "optionB": "O(log n)",
      "optionC": "O(n^2)",
      "optionD": "O(1)",
      "correctAnswer": "B"
    }
  ]
}
```

### Get Quizzes by Document

```
GET /api/quizzes/{documentId}
```

---

## Sharing

**Two distinct features:**

| Concept | Operation | Effect |
|---------|-----------|--------|
| **Share** | Grant access | Recipient has **read-only** access. Ownership never changes. |
| **Save to My Folder** | Copy | Creates a **personal copy** owned by the requester. Original unchanged. |

### Get Shares by Owner

```
GET /api/shares/owner
```

### Get Shares with Me

```
GET /api/shares/shared-with-me
```

### Delete Share

```
DELETE /api/shares/{id}
```

### Delete Share by Token

```
DELETE /api/shares/token/{shareToken}
```

### Save Shared Content to My Folder

```
POST /api/shares/{shareId}/save
```

**Request:**
```json
{
  "folderId": "uuid",
  "title": "Copied document",
  "description": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| folderId | UUID (required) | Destination folder (belongs to the requester) |
| title | String (optional) | Override title; only applies to single-document shares |
| description | String (optional) | Override description; only applies to single-document shares |

**Behavior**: This is a **COPY** operation, not a transfer of ownership.
- A personal copy of each shared document is created inside the requester's folder
- Copied resources belong entirely to the requester (`ownerId` = requester)
- Original shared resources and their owner are completely unchanged
- `document.summary` is preserved (not regenerated)
- Flashcards and quizzes are **not** copied

**Duplicate Prevention** (checked in this order):
1. `checksum` matches an existing document in the destination folder
2. `publicId` (Cloudinary) matches an existing document
3. `title` AND `fileSize` both match an existing document

**Response** `200`:
```json
{
  "total": 6,
  "copied": 4,
  "skipped": 2,
  "failed": 0,
  "copiedDocuments": [
    { "name": "Week1.pdf", "documentId": "uuid-1", "reason": null },
    { "name": "Chapter2.pdf", "documentId": "uuid-2", "reason": null }
  ],
  "skippedDocuments": [
    { "name": "Java Basics.pdf", "documentId": null, "reason": "Already exists" },
    { "name": "Spring Boot.pdf", "documentId": null, "reason": "Already exists" }
  ],
  "failedDocuments": [],
  "message": "4 documents copied successfully. 2 documents were skipped because they already exist."
}
```

All-zero cases covered:
```json
// Everything copied
{ "total": 6, "copied": 6, "skipped": 0, "failed": 0,
  "copiedDocuments": [...], "skippedDocuments": [], "failedDocuments": [],
  "message": "6 documents copied successfully" }

// Everything already exists
{ "total": 6, "copied": 0, "skipped": 6, "failed": 0,
  "copiedDocuments": [], "skippedDocuments": [...], "failedDocuments": [],
  "message": "6 documents were skipped because they already exist" }

// Everything failed
{ "total": 6, "copied": 0, "skipped": 0, "failed": 6,
  "copiedDocuments": [], "skippedDocuments": [], "failedDocuments": [...],
  "message": "6 documents failed to copy" }
```

---

## Admin

All admin endpoints require `ROLE_ADMIN`.

### Dashboard Stats

```
GET /api/admin/dashboard/stats
```

### Users

```
GET /api/admin/users
```

### Documents

```
GET /api/admin/documents
GET /api/admin/documents/trash
PATCH /api/admin/documents/{id}/approve
PATCH /api/admin/documents/{id}/reject
POST /api/admin/documents/{id}/restore
GET /api/admin/documents/status/{status}
```

### Payments

```
GET /api/admin/payments
```

---

## Response Format

All API responses follow the `ApiResponse<T>` wrapper:

```json
{
  "code": 200,
  "message": "Success",
  "data": { ... }
}
```

Error responses:
```json
{
  "code": 400,
  "message": "Validation failed",
  "data": null
}
```

---

## Common HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content (delete success) |
| 400 | Bad Request / Validation Error |
| 401 | Unauthorized (missing/invalid JWT) |
| 403 | Forbidden (wrong role/permission) |
| 404 | Not Found |
| 500 | Internal Server Error |

---

## AI Feature Behaviors

### `force=false` (default)
- AI Summary: returns cached `Document.summary` if non-empty
- Flashcards: returns existing flashcards for this document if any
- Quiz: returns existing quiz for this document if any

### `force=true`
- AI Summary: calls AI → overwrites `Document.summary` → returns new markdown
- Flashcards: deletes all flashcards for this document → generates new → saves → returns
- Quiz: deletes all quizzes + questions for this document → generates new → saves → returns
