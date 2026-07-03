# AI Generation API Changes - Multiple Documents Support

**Last Updated**: 2026-07-04  
**Status**: ✅ Completed & Compiled Successfully

## 📋 Overview

Updated AI generation features (Summary, Quiz, Flashcard) to support generating from **1 or multiple documents** instead of just single document.

---

## ✅ Current Status - What Changed

### Summary API
- ✅ **Already supported** multiple documents
- Uses: `List<UUID> documentIds`
- Endpoint: `POST /api/ai/summary`

### Quiz API
- ❌ **Was**: Single document only (`UUID documentId`)
- ✅ **Now**: Multiple documents supported (`List<UUID> documentIds`)
- Endpoint: `POST /api/quizzes/generate`

### Flashcard API
- ❌ **Was**: Single document only (`UUID documentId`)
- ✅ **Now**: Multiple documents supported (`List<UUID> documentIds`)
- Endpoint: `POST /api/flashcards/generate`

---

## 📝 API Documentation

### 1. Generate Summary (Existing - Already Multi-Doc)

**Endpoint**: `POST /api/ai/summary`

**Request Body**:
```json
{
  "documentIds": ["uuid-1", "uuid-2", "uuid-3"],
  "force": false
}
```

**Parameters**:
- `documentIds` (required, List<UUID>): Document IDs to generate summary from. Can be 1 or multiple.
- `force` (optional, boolean): If true, regenerate summaries even if they exist. Default: false

**Response**:
```json
{
  "success": true,
  "message": "AI Summary generated successfully",
  "data": {
    "markdown": "--- Document Title 1 ---\n...\n\n--- Document Title 2 ---\n..."
  }
}
```

**Status Codes**:
- 200 OK: Summary generated successfully
- 400 Bad Request: No accessible documents
- 401 Unauthorized: User not authenticated
- 500 Internal Server Error: AI generation failed

---

### 2. Generate Quiz (NEW - Now Multi-Doc)

**Endpoint**: `POST /api/quizzes/generate`

**Request Body - OLD (Single Document)**:
```json
{
  "documentId": "uuid-of-document",
  "numberOfQuestions": 5
}
```

**Request Body - NEW (Multiple Documents)**:
```json
{
  "documentIds": ["uuid-1", "uuid-2"],
  "numberOfQuestions": 10
}
```

**Parameters**:
- `documentIds` (required, List<UUID>): Document IDs to generate quiz from. Can be 1 or multiple.
- `numberOfQuestions` (required, Integer ≥ 1): Number of questions to generate

**Response**:
```json
{
  "id": 123,
  "title": "AI-Generated Quiz from 2 document(s)",
  "generatedByAi": true,
  "createdAt": "2026-07-04T10:30:00",
  "questions": [
    {
      "id": 1,
      "content": "What is machine learning?",
      "optionA": "...",
      "optionB": "...",
      "optionC": "...",
      "optionD": "...",
      "correctAnswer": "A"
    }
  ]
}
```

**Status Codes**:
- 201 Created: Quiz generated successfully
- 400 Bad Request: No accessible documents or invalid parameters
- 401 Unauthorized: User not authenticated
- 500 Internal Server Error: AI generation failed

---

### 3. Generate Flashcards (NEW - Now Multi-Doc)

**Endpoint**: `POST /api/flashcards/generate`

**Request Body - OLD (Single Document)**:
```json
{
  "documentId": "uuid-of-document",
  "numberOfCards": 10
}
```

**Request Body - NEW (Multiple Documents)**:
```json
{
  "documentIds": ["uuid-1", "uuid-2", "uuid-3"],
  "numberOfCards": 15
}
```

**Parameters**:
- `documentIds` (required, List<UUID>): Document IDs to generate flashcards from. Can be 1 or multiple.
- `numberOfCards` (required, Integer ≥ 1): Number of flashcards to generate

**Response**:
```json
[
  {
    "id": 1,
    "documentId": "uuid-1",
    "frontContent": "What is photosynthesis?",
    "backContent": "Process where plants convert sunlight into chemical energy...",
    "generatedByAi": true,
    "createdAt": "2026-07-04T10:30:00"
  },
  {
    "id": 2,
    "documentId": "uuid-1",
    "frontContent": "...",
    "backContent": "...",
    "generatedByAi": true,
    "createdAt": "2026-07-04T10:30:00"
  }
]
```

**Status Codes**:
- 201 Created: Flashcards generated successfully
- 400 Bad Request: No accessible documents or invalid parameters
- 401 Unauthorized: User not authenticated
- 500 Internal Server Error: AI generation failed

---

## 🔧 Backend Changes

### DTOs Modified

#### 1. GenerateQuizRequest.java
**Changed from**:
```java
@Data
public class GenerateQuizRequest {
    @NotNull(message = "Document ID cannot be null")
    private UUID documentId;
    
    @Min(value = 1, message = "Number of questions must be at least 1")
    private Integer numberOfQuestions;
}
```

**Changed to**:
```java
@Data
public class GenerateQuizRequest {
    @NotEmpty(message = "Document IDs must not be empty")
    private List<UUID> documentIds;
    
    @Min(value = 1, message = "Number of questions must be at least 1")
    private Integer numberOfQuestions;
}
```

---

#### 2. GenerateFlashcardsRequest.java
**Changed from**:
```java
@Data
public class GenerateFlashcardsRequest {
    @NotNull(message = "Document ID cannot be null")
    private UUID documentId;
    
    @Min(value = 1, message = "Number of cards must be at least 1")
    private Integer numberOfCards;
}
```

**Changed to**:
```java
@Data
public class GenerateFlashcardsRequest {
    @NotEmpty(message = "Document IDs must not be empty")
    private List<UUID> documentIds;
    
    @Min(value = 1, message = "Number of cards must be at least 1")
    private Integer numberOfCards;
}
```

---

### Service Interfaces Modified

#### 1. QuizService.java
**Changed from**:
```java
QuizResponse generateQuiz(UUID documentId, UUID requesterId, GenerateQuizRequest request) throws Exception;
```

**Changed to**:
```java
QuizResponse generateQuiz(List<UUID> documentIds, UUID requesterId, GenerateQuizRequest request) throws Exception;
```

---

#### 2. FlashcardService.java
**Changed from**:
```java
List<FlashcardResponse> generateFlashcards(UUID documentId, UUID requesterId, GenerateFlashcardsRequest request) throws Exception;
```

**Changed to**:
```java
List<FlashcardResponse> generateFlashcards(List<UUID> documentIds, UUID requesterId, GenerateFlashcardsRequest request) throws Exception;
```

---

### Service Implementations

#### QuizServiceImpl.java
- Now uses `DocumentSourceResolver.resolveByDocumentIds()` to resolve multiple documents
- Uses `KnowledgePreparationService.prepareKnowledge()` to merge all documents
- Authorizes all documents before generation
- Quiz title changed to: `"AI-Generated Quiz from N document(s)"`
- Stores first document ID in quiz.documentId (for backward compatibility)

#### FlashcardServiceImpl.java
- Now uses `DocumentSourceResolver.resolveByDocumentIds()` to resolve multiple documents
- Uses `KnowledgePreparationService.prepareKnowledge()` to merge all documents
- Authorizes all documents before generation
- Stores first document ID in flashcard.documentId (for backward compatibility)

---

### Controllers Modified

#### QuizController.java
```java
@PostMapping("/generate")
public ResponseEntity<QuizResponse> generateQuiz(
        @Valid @RequestBody GenerateQuizRequest request,
        Authentication authentication) throws Exception {
    UUID requesterId = getCurrentUserId(authentication);
    QuizResponse response = quizService.generateQuiz(
            request.getDocumentIds(),  // Changed from getDocumentId()
            requesterId,
            request
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

#### FlashcardController.java
```java
@PostMapping("/generate")
public ResponseEntity<List<FlashcardResponse>> generateFlashcards(
        @Valid @RequestBody GenerateFlashcardsRequest request,
        Authentication authentication) throws Exception {
    UUID requesterId = getCurrentUserId(authentication);
    List<FlashcardResponse> responses = flashcardService.generateFlashcards(
            request.getDocumentIds(),  // Changed from getDocumentId()
            requesterId,
            request
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(responses);
}
```

---

## 🎨 Frontend Changes Required

### 1. Update Request DTOs

**OLD - quizRequest.ts**:
```typescript
interface GenerateQuizRequest {
  documentId: UUID;
  numberOfQuestions: number;
}
```

**NEW - quizRequest.ts**:
```typescript
interface GenerateQuizRequest {
  documentIds: UUID[];  // Array instead of single UUID
  numberOfQuestions: number;
}
```

---

**OLD - flashcardRequest.ts**:
```typescript
interface GenerateFlashcardsRequest {
  documentId: UUID;
  numberOfCards: number;
}
```

**NEW - flashcardRequest.ts**:
```typescript
interface GenerateFlashcardsRequest {
  documentIds: UUID[];  // Array instead of single UUID
  numberOfCards: number;
}
```

---

### 2. Update API Calls

**OLD - quizApi.ts**:
```typescript
export const quizApi = {
  generateQuiz: async (documentId: UUID, numberOfQuestions: number) => {
    return api<QuizResponse>("/api/quizzes/generate", {
      method: "POST",
      body: {
        documentId,
        numberOfQuestions,
      },
    });
  },
};
```

**NEW - quizApi.ts**:
```typescript
export const quizApi = {
  generateQuiz: async (documentIds: UUID[], numberOfQuestions: number) => {
    return api<QuizResponse>("/api/quizzes/generate", {
      method: "POST",
      body: {
        documentIds,  // Pass array
        numberOfQuestions,
      },
    });
  },
};
```

---

**OLD - flashcardApi.ts**:
```typescript
export const flashcardApi = {
  generateFlashcards: async (documentId: UUID, numberOfCards: number) => {
    return api<FlashcardResponse[]>("/api/flashcards/generate", {
      method: "POST",
      body: {
        documentId,
        numberOfCards,
      },
    });
  },
};
```

**NEW - flashcardApi.ts**:
```typescript
export const flashcardApi = {
  generateFlashcards: async (documentIds: UUID[], numberOfCards: number) => {
    return api<FlashcardResponse[]>("/api/flashcards/generate", {
      method: "POST",
      body: {
        documentIds,  // Pass array
        numberOfCards,
      },
    });
  },
};
```

---

### 3. Update UI Components

**When calling from Components:**

**OLD**:
```typescript
const handleGenerateQuiz = async (documentId: UUID) => {
  const response = await quizApi.generateQuiz(documentId, 5);
  // ...
};
```

**NEW - Single Document**:
```typescript
const handleGenerateQuiz = async (documentId: UUID) => {
  const response = await quizApi.generateQuiz([documentId], 5);  // Wrap in array
  // ...
};
```

**NEW - Multiple Documents**:
```typescript
const handleGenerateQuiz = async (selectedDocumentIds: UUID[]) => {
  const response = await quizApi.generateQuiz(selectedDocumentIds, 5);
  // ...
};
```

---

### 4. Update Document Selection UI

**OLD - Single Selection**:
```typescript
const [selectedDocumentId, setSelectedDocumentId] = useState<UUID | null>(null);

<Select value={selectedDocumentId} onChange={setSelectedDocumentId}>
  {documents.map(doc => (
    <option key={doc.id} value={doc.id}>{doc.title}</option>
  ))}
</Select>

<button onClick={() => handleGenerateQuiz(selectedDocumentId)}>Generate</button>
```

**NEW - Multi Selection**:
```typescript
const [selectedDocumentIds, setSelectedDocumentIds] = useState<UUID[]>([]);

<MultiSelect 
  value={selectedDocumentIds} 
  onChange={setSelectedDocumentIds}
>
  {documents.map(doc => (
    <option key={doc.id} value={doc.id}>{doc.title}</option>
  ))}
</MultiSelect>

<button onClick={() => handleGenerateQuiz(selectedDocumentIds)}>Generate</button>
```

---

## 📊 Data Flow Example

### Scenario: Generate Quiz from 3 Documents

**Frontend Request**:
```typescript
await quizApi.generateQuiz(
  ["uuid-doc1", "uuid-doc2", "uuid-doc3"],
  10
);
```

**HTTP Request**:
```
POST /api/quizzes/generate
Content-Type: application/json
Authorization: Bearer <token>

{
  "documentIds": ["uuid-doc1", "uuid-doc2", "uuid-doc3"],
  "numberOfQuestions": 10
}
```

**Backend Processing**:
1. Validate authentication
2. Resolve 3 documents via `DocumentSourceResolver.resolveByDocumentIds()`
3. Authorize all 3 documents (check ownership)
4. Merge content using `KnowledgePreparationService.prepareKnowledge()`
5. Generate 10 questions from merged content
6. Save quiz (linked to first document for compatibility)
7. Return QuizResponse with all questions

**Frontend Response**:
```json
{
  "id": 123,
  "title": "AI-Generated Quiz from 3 document(s)",
  "generatedByAi": true,
  "questions": [
    { "id": 1, "content": "...", ... },
    { "id": 2, "content": "...", ... },
    ...
  ]
}
```

---

## 🔐 Security Notes

- ✅ All documents are authorized before generation
- ✅ User can only generate from their own documents
- ✅ Admin can generate from any document
- ✅ No changes to authentication/authorization logic
- ✅ documentIds list is validated (must not be empty)

---

## 📝 Migration Guide

### For Frontend Teams

1. **Update type definitions** in request DTOs
2. **Update API call functions** to accept array instead of single UUID
3. **Update UI components** to support multi-select or keep single-select (wrap in array)
4. **Test with single document** first (backward compatible flow)
5. **Test with multiple documents** to ensure merge works correctly

### For API Consumers

- Old single-document requests: **Wrap documentId in array**: `[documentId]`
- New multi-document requests: Pass array directly
- Response format unchanged (backward compatible)

---

## ✅ Build Status

```
[INFO] BUILD SUCCESS
[INFO] Total time: 8.231 s
[INFO] Warnings: 3 (mapper unmapped properties - not related to changes)
```

All changes compiled successfully with no errors.

---

## 📋 Files Changed

Backend:
- ✅ `src/main/java/com/tugnw/aistudy/domain/dto/quiz/GenerateQuizRequest.java`
- ✅ `src/main/java/com/tugnw/aistudy/domain/dto/flashcard/GenerateFlashcardsRequest.java`
- ✅ `src/main/java/com/tugnw/aistudy/service/QuizService.java`
- ✅ `src/main/java/com/tugnw/aistudy/service/FlashcardService.java`
- ✅ `src/main/java/com/tugnw/aistudy/service/impl/QuizServiceImpl.java`
- ✅ `src/main/java/com/tugnw/aistudy/service/impl/FlashcardServiceImpl.java`
- ✅ `src/main/java/com/tugnw/aistudy/controller/QuizController.java`
- ✅ `src/main/java/com/tugnw/aistudy/controller/FlashcardController.java`

Frontend:
- 🔄 `src/api/quizApi.ts` (needs update)
- 🔄 `src/api/flashcardApi.ts` (needs update)
- 🔄 Quiz generation components (needs update)
- 🔄 Flashcard generation components (needs update)

---

**End of API Changes Documentation**
