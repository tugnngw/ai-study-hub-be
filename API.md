# AI Study Hub — API Reference

**All IDs are UUID strings.** No numeric IDs remain.
Every entity identifier is a UUID v4 string like `"550e8400-e29b-41d4-a716-446655440000"`.

---

## Academic Structure

```
Semester → Subject → Folder → Document → AI Features
```

### Upload Workflow (frontend order)

1. `GET /api/semesters` → pick a Semester
2. `GET /api/subjects/semester/{semesterId}` → pick a Subject
3. `POST /api/folder/create` → create a Folder under that Subject (or use existing)
4. `POST /api/documents` (multipart) → upload into the Folder

Folders are the upload destination. Every Folder belongs to one Subject.
Documents inherit their Subject through the Folder. No direct Subject-to-Document mapping.

---

## UUID Rule

**Every** `id`, `semesterId`, `subjectId`, `folderId`, `documentId`, `ownerId`,
`quizId`, `questionId`, `flashcardId`, `shareId`, `planId` in the API is a
**UUID v4 string**.

Example:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "semesterId": "660e8400-e29b-41d4-a716-446655440001"
}
```

---

## Default Subject

Every Semester auto-creates one built-in Subject named "General" with
`defaultSubject: true`. This subject:

- Cannot be deleted
- Cannot be renamed
- Behaves identically to any other Subject for folders and uploads

**Frontend must use `defaultSubject` (boolean), never compare the name "General".**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "General",
  "defaultSubject": true
}
```

Display a badge/icon when `defaultSubject === true`. Disable rename and delete
buttons for that subject.

---

## Response Envelope

Every response uses `ApiResponse<T>`:

```json
{
  "code": 200,
  "message": "Success",
  "data": { ... }
}
```

Error:
```json
{
  "code": 400,
  "message": "Validation failed",
  "data": null
}
```

---

## Authentication

### Auth header
```
Authorization: Bearer <jwt-access-token>
```

### Register

```
POST /api/auth/register
```

**Request:**
```json
{
  "username": "john_doe",
  "password": "SecurePass123",
  "fullName": "John Doe"
}
```

| Field | Type | Rules |
|-------|------|-------|
| username | string | required, 3-50 chars, alphanumeric + underscores |
| password | string | required, 8-128 chars |
| fullName | string | required, max 30 chars |

**Response 200:**
```json
{
  "code": 200,
  "message": "Registered",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "fullName": "John Doe",
    "role": "USER",
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 900000
  }
}
```

### Login

```
POST /api/auth/login
```

**Request:**
```json
{
  "username": "john_doe",
  "password": "SecurePass123"
}
```

**Response 200:** Same shape as Register.

### Refresh Token

```
POST /api/auth/refresh
```

**Request:**
```json
{
  "refreshToken": "eyJhbGciOi..."
}
```

**Response 200:** Same shape as Register (new accessToken and refreshToken).

### Logout

```
POST /api/auth/logout
```

**Response 200:** `{ "code": 200, "message": "Logged out", "data": null }`

---

## Account

### Get Current User

```
GET /api/account/me
```

**Headers:** `Authorization: Bearer <token>`

**Response 200:**
```json
{
  "code": 200,
  "message": "User retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
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

`GET /api/semesters` (public)

**Response 200:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Spring 2025",
    "startDate": "2025-01-15",
    "endDate": "2025-05-20"
  }
]
```

### Get Semester by ID

`GET /api/semesters/{id}` (public)

**Response 200:** Single semester object (same shape as list item).

### Create Semester (Admin)

`POST /api/semesters`

**Auth:** ADMIN

**Request:**
```json
{
  "name": "Fall 2025",
  "startDate": "2025-09-01",
  "endDate": "2025-12-20"
}
```

**Response 201:** Semester object.

**Side effect:** A default Subject named "General" with `defaultSubject: true` is
automatically created in this semester.

### Delete Semester (Admin)

`DELETE /api/semesters/{id}`

**Auth:** ADMIN

**Response 204:** No content.

---

## Subjects

### List Subjects by Semester

`GET /api/subjects/semester/{semesterId}` (public)

**Response 200:**
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "semesterId": "550e8400-e29b-41d4-a716-446655440000",
    "code": "SWP391",
    "name": "Software Development Project",
    "defaultSubject": false
  },
  {
    "id": "770e8400-e29b-41d4-a716-446655440002",
    "semesterId": "550e8400-e29b-41d4-a716-446655440000",
    "code": null,
    "name": "General",
    "defaultSubject": true
  }
]
```

### Get Subject by ID

`GET /api/subjects/{id}` (public)

**Response 200:** Single subject object.

### Create Subject (Admin)

`POST /api/subjects`

**Auth:** ADMIN

**Request:**
```json
{
  "semesterId": "550e8400-e29b-41d4-a716-446655440000",
  "code": "SWP391",
  "name": "Software Development Project"
}
```

`defaultSubject` is always `false` for user-created subjects.

**Response 201:** Subject object.

### Delete Subject (Admin)

`DELETE /api/subjects/{id}`

**Auth:** ADMIN

**Response 204:** No content.

**Fails** with 400 if the subject has `defaultSubject: true`. The default
subject cannot be deleted.

---

## Folders

### Create Folder

`POST /api/folder/create`

**Auth:** User

**Request:**
```json
{
  "name": "Chapter 1: Introduction",
  "subjectId": "660e8400-e29b-41d4-a716-446655440001"
}
```

| Field | Type | Rules |
|-------|------|-------|
| name | string | required, max 100 chars, unique per user |
| subjectId | UUID | required, must exist |

**Response 201:**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "name": "Chapter 1: Introduction",
  "aiSummary": null,
  "subjectId": "660e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:00",
  "documentCount": 0
}
```

### List User's Folders

`GET /api/folder/getall`

**Auth:** User

**Response 200:** Array of FolderResponse.

### Get Folder by ID

`GET /api/folder/getbyid/{id}`

**Auth:** User (or ADMIN)

**Response 200:** FolderResponse.

**403** if the folder belongs to another user and caller is not ADMIN.

### Update Folder

`PUT /api/folder/update/{id}`

**Auth:** User (owner)

**Request:**
```json
{
  "name": "Updated Name",
  "subjectId": "770e8400-e29b-41d4-a716-446655440002"
}
```

Both fields optional. Only provided fields are updated.

**Response 200:** Updated FolderResponse.

### Delete Folder (Soft)

`DELETE /api/folder/delete/{id}`

**Auth:** User (owner) or ADMIN

**Response 204:** No content.

Sets `deletedAt` timestamp. Data is preserved but hidden from normal queries.

### Share Folder

`POST /api/folder/{id}/share`

**Auth:** User (owner)

**Request:**
```json
{
  "email": "friend@example.com",
  "visibility": "private"
}
```

Either `email` or `username` identifies the target user. If both are omitted,
creates a link-only share (no specific recipient).

**Response 200:** ShareResponse (see Shares section).

### Get Folder Share Info

`GET /api/folder/{id}/share-info`

**Auth:** User (owner)

**Response 200:** ShareResponse with recipient list.

---

## Documents

### Upload Documents

`POST /api/documents`

**Auth:** User

**Content-Type:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| files | MultipartFile[] | Yes | PDF, DOCX, TXT, PPTX. Max 50MB each. |
| title | string | No | Document title. If omitted, uses filename. |
| description | string | No | Optional description. |
| folderId | UUID | No | Destination folder. |

**Response 201:**
```json
[
  {
    "id": "990e8400-e29b-41d4-a716-446655440004",
    "ownerId": "550e8400-e29b-41d4-a716-446655440000",
    "folderId": "880e8400-e29b-41d4-a716-446655440003",
    "subjectId": "660e8400-e29b-41d4-a716-446655440001",
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

Returns an array — one entry per uploaded file.

### List User's Documents

`GET /api/documents`

**Auth:** User

**Response 200:** Array of DocumentResponse belonging to the authenticated user.

### List Documents by Folder

`GET /api/documents/folder/{folderId}`

**Auth:** User (owner)

**Response 200:** Array of DocumentResponse in the folder.

### List Shared Folder Documents

`GET /api/documents/shared/folder/{folderId}`

**Auth:** User (must have share access)

**Response 200:** Array of DocumentResponse with status "READY".

### Get Document by ID

`GET /api/documents/{id}`

**Auth:** User (owner) or share recipient

**Response 200:** DocumentResponse.

### Get Shared Document by ID

`GET /api/documents/shared/{id}`

**Auth:** User (share recipient)

**Response 200:** DocumentResponse.

### Update Document

`PUT /api/documents/{id}`

**Auth:** User (owner)

**Request:**
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "folderId": "880e8400-e29b-41d4-a716-446655440003"
}
```

All fields optional.

**Response 200:** Updated DocumentResponse.

### Delete Document (Soft)

`DELETE /api/documents/{id}`

**Auth:** User (owner)

**Response 204:** No content.

### Trash

#### List Trash

`GET /api/documents/trash`

**Auth:** User

**Response 200:** Array of soft-deleted DocumentResponse.

#### Restore from Trash

`POST /api/documents/{id}/restore`

**Auth:** User (owner)

**Response 200:** No content.

### Download

`GET /api/documents/{id}/download`

**Auth:** User (owner or share recipient)

**Response 200:** Cloudinary download URL (string).

### Share Document

`POST /api/documents/{id}/share`

**Auth:** User (owner)

**Request:**
```json
{
  "email": "friend@example.com",
  "visibility": "private"
}
```

Same logic as Folder share.

### Get Document Share Info

`GET /api/documents/{id}/share-info`

**Auth:** User (owner)

**Response 200:** ShareResponse.

---

## AI Summary

Every AI feature belongs to **one document**. AI features cannot span multiple documents.

### Generate or Regenerate Summary

`POST /api/ai/summary`

**Auth:** User (document owner)

**Request:**
```json
{
  "documentId": "990e8400-e29b-41d4-a716-446655440004",
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID | — | Document to summarize (required) |
| force | boolean | false | `false` → return cached summary if exists; `true` → regenerate and overwrite |

**Response 200:**
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

`GET /api/ai/summary/{documentId}`

**Auth:** User (document owner)

**Response 200:**
```json
{
  "code": 200,
  "message": "Cached summary retrieved",
  "data": {
    "markdown": "# Key Concepts\n\n..."
  }
}
```

Returns `markdown: ""` if no cached summary exists (never calls AI).

---

## Flashcards

### Generate Flashcards

`POST /api/flashcards/generate`

**Auth:** User (document owner)

**Request:**
```json
{
  "documentId": "990e8400-e29b-41d4-a716-446655440004",
  "numberOfCards": 10,
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID | — | Document to generate from (required) |
| numberOfCards | integer | — | Number of flashcards (min: 1) |
| force | boolean | false | `false` → return existing cards; `true` → delete old, generate new |

**Response 201:**
```json
[
  {
    "id": "aa0e8400-e29b-41d4-a716-446655440005",
    "frontContent": "What is polymorphism?",
    "backContent": "The ability of objects to take multiple forms...",
    "generatedByAi": true,
    "createdAt": "2025-01-15T10:00:00"
  }
]
```

### Get Flashcards by Document

`GET /api/flashcards/{documentId}`

**Auth:** User (document owner)

**Response 200:** Array of FlashcardResponse (same shape as generate response).

---

## Quizzes

### Generate Quiz

`POST /api/quizzes/generate`

**Auth:** User (document owner)

**Request:**
```json
{
  "documentId": "990e8400-e29b-41d4-a716-446655440004",
  "numberOfQuestions": 5,
  "force": false
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| documentId | UUID | — | Document to generate from (required) |
| numberOfQuestions | integer | — | Number of questions (min: 1) |
| force | boolean | false | `false` → return existing quiz; `true` → delete old, generate new |

**Response 201:**
```json
{
  "id": "bb0e8400-e29b-41d4-a716-446655440006",
  "title": "AI-Generated Quiz",
  "generatedByAi": true,
  "createdAt": "2025-01-15T10:00:00",
  "questions": [
    {
      "id": "cc0e8400-e29b-41d4-a716-446655440007",
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

`correctAnswer` is a single letter (`A`, `B`, `C`, or `D`). The frontend uses
it for grading user selections.

### Get Quizzes by Document

`GET /api/quizzes/{documentId}`

**Auth:** User (document owner)

**Response 200:** Array of QuizResponse.

---

## Shares

### Two Distinct Operations

| Concept | Operation | Effect |
|---------|-----------|--------|
| **Share** | Grant read-only access | Recipient views the original. Ownership unchanged. |
| **Save to My Folder** | Copy to own workspace | Recipient receives personal copies. Ownership transfers. |

### Get Shares by Owner

`GET /api/shares/owner`

**Auth:** User

**Response 200:** Array of ShareResponse.

### Get Shares with Me

`GET /api/shares/shared-with-me`

**Auth:** User

**Response 200:** Array of ShareResponse filtered to items shared with the
caller.

### Delete Share

`DELETE /api/shares/{id}`

**Auth:** User (share owner) or ADMIN

**Response 204:** No content.

### Delete Share by Token

`DELETE /api/shares/token/{shareToken}`

**Auth:** User (share owner or shared recipient) or ADMIN

**Response 204:** No content.

### Save Shared Content to My Folder

`POST /api/shares/{shareId}/save`

**Auth:** User

**Request:**
```json
{
  "folderId": "880e8400-e29b-41d4-a716-446655440003",
  "title": "Copied document (optional)",
  "description": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| folderId | UUID | Destination folder (required, belongs to requester) |
| title | string | Override title (optional, single-document shares only) |
| description | string | Override description (optional, single-document shares only) |

**Behavior:** COPY operation (not transfer).
- Personal copy created in requester's folder
- `ownerId` = requester
- Original unchanged
- `document.summary` preserved
- Flashcards/quizzes NOT copied

**Duplicate Prevention** (checked in order):
1. `checksum` match → skip
2. `publicId` (Cloudinary) match → skip
3. `title` + `fileSize` match → skip

**Response 200:**
```json
{
  "total": 6,
  "copied": 4,
  "skipped": 2,
  "failed": 0,
  "copiedDocuments": [
    { "name": "Week1.pdf", "documentId": "990e8400-...", "reason": null }
  ],
  "skippedDocuments": [
    { "name": "Java Basics.pdf", "documentId": null, "reason": "Already exists" }
  ],
  "failedDocuments": [],
  "message": "4 documents copied successfully. 2 documents were skipped because they already exist."
}
```

---

## Reports

### Create Report

`POST /api/reports`

**Auth:** User

**Request:**
```json
{
  "documentId": "990e8400-e29b-41d4-a716-446655440004",
  "reason": "Inappropriate content",
  "description": "Optional details"
}
```

**Response 200:** `{ "code": 200, "message": "Report submitted", "data": null }`

### List Reports (Admin)

`GET /api/reports`

**Auth:** ADMIN

**Query:** Pagination (`page`, `size`, `sort`)

**Response 200:** Paginated list of ReportResponse.

### List My Reports

`GET /api/reports/my`

**Auth:** User

**Response 200:** Paginated list of ReportResponse by the caller.

### List All Reports History (Admin)

`GET /api/reports/history`

**Auth:** ADMIN

**Response 200:** Paginated list of all ReportResponse.

### Handle Report Decision (Admin)

`POST /api/reports/{id}/decision`

**Auth:** ADMIN

**Request:**
```json
{
  "decision": "approved"
}
```

Decisions: `"approved"`, `"rejected"`, `"removed"`.

Approving a report sets the document's status to `"REJECT"` (hidden from normal
queries).

**Response 200:** `{ "code": 200, "message": "Report decision processed", "data": null }`

---

## Admin

All admin endpoints require `ROLE_ADMIN`.

### Dashboard Stats

`GET /api/admin/dashboard/stats`

**Response 200:**
```json
{
  "code": 200,
  "message": "Dashboard stats",
  "data": {
    "totalUsers": 150,
    "totalUsersTrend": 12.5,
    "totalDocs": 420,
    "totalDocsTrend": 8.3,
    "totalDownloads": 89,
    "totalDownloadsTrend": -2.1
  }
}
```

### Recent Activity

`GET /api/admin/dashboard/activity?limit=15`

**Response 200:**
```json
{
  "code": 200,
  "message": "Recent activities",
  "data": [
    {
      "id": "uuid",
      "title": "Uploaded document: Chapter1.pdf",
      "actor": "john_doe",
      "type": "upload",
      "time": "5 phút trước",
      "createdAt": "2025-01-15T10:00:00Z"
    }
  ]
}
```

### Users (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/users` | List all users (paginated) |
| GET | `/api/admin/users/{id}` | Get user by ID |
| PATCH | `/api/admin/users/{id}/lock` | Lock a user |
| PATCH | `/api/admin/users/{id}/unlock` | Unlock a user |
| PATCH | `/api/admin/users/{id}/toggle-status` | Toggle lock/unlock |
| DELETE | `/api/admin/users/{id}` | Soft-delete a user |
| PATCH | `/api/admin/users/{id}/restore` | Restore soft-deleted user |

### Documents (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/documents` | List all documents |
| GET | `/api/admin/documents/trash` | List trashed documents |
| GET | `/api/admin/documents/status/{status}` | Filter by status |
| PATCH | `/api/admin/documents/{id}/approve` | Approve document (sets status to READY) |
| PATCH | `/api/admin/documents/{id}/reject` | Reject document (sets status to REJECT) |
| POST | `/api/admin/documents/{id}/restore` | Restore from trash |

### Transactions (Admin)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/transactions` | All transactions (paginated) |
| GET | `/api/admin/transactions/status/{status}` | Filter by status |
| GET | `/api/admin/transactions/user/{accountId}` | By user |

---

## Payments

### List Active Plans

`GET /api/payment/plans`

**Auth:** No auth required

**Response 200:** Array of PaymentPlan.

### Create Payment Link

`POST /api/payment/create`

**Auth:** User

**Request:**
```json
{
  "planId": "dd0e8400-e29b-41d4-a716-446655440008"
}
```

**Response 200:**
```json
{
  "checkoutUrl": "https://payos.vn/checkout/...",
  "orderCode": 123456789,
  "amount": 99000
}
```

### Check Payment Status

`GET /api/payment/status/{orderCode}`

### Webhook (PayOS)

`POST /api/payment/webhook`

PayOS callback endpoint.

### My Transactions

`GET /api/payment/my-transactions`

**Auth:** User

**Response 200:** Array of user's payment transactions.

---

## AI Feature Behaviors

### `force=false` (default, read-cached)

| Feature | Returns |
|---------|---------|
| Summary | Cached `Document.summary` if non-empty, else generates new |
| Flashcards | Existing flashcards for this document if any |
| Quiz | Existing quiz for this document if any |

### `force=true` (regenerate)

| Feature | Action |
|---------|--------|
| Summary | Calls AI → overwrites `Document.summary` → returns new markdown |
| Flashcards | Deletes all flashcards for this document → generates new → saves |
| Quiz | Deletes all quizzes + questions for this document → generates new → saves |

Every AI feature belongs to exactly one document. No multi-document generation.

---

## Frontend Integration Notes

1. **All IDs are UUID strings.** Pass them as-is in URLs and JSON bodies.
2. **Subject.defaultSubject** — use this boolean. Never compare `name === "General"`.
3. **Folder is the upload destination.** Documents go into a Folder, which belongs to a Subject. The hierarchy is always Semester → Subject → Folder → Document.
4. **Every AI feature belongs to one document.** No batch/collection AI features.
5. **Backend is frozen after UUID migration.** No further schema changes are expected.

---

## Removed Features

The following features no longer exist in this API:

- NotebookLM integration
- Multi-document AI generation
- `documentIds[]` array parameter (all AI endpoints accept a single `documentId`)
- Knowledge Sources / Document selector
- Subject name comparison (use `defaultSubject` flag)

---

## HTTP Status Codes

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
