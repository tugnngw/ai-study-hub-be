# UUID Identifiers & Default Subject — Implementation Notes

**Date:** 2026-07-12

---

## Part 1: UUID Standardization

### Before
The project mixed identifier types:
- **UUID:** Account, Folder, Document, ActivityLog, PaymentTransaction, Report
- **Long (BIGSERIAL):** Semester, Subject, Share, DocumentChunk, Flashcard, Quiz, Question, QuizAttempt, QuizAnswer, FlashcardProgress, PaymentPlan

### After
**Every entity uses UUID** (`GenerationType.UUID`).

### Migration (V11__unify_ids_to_uuid.sql)
- Creates temp mapping tables for each converted table (old BIGINT → new UUID)
- Adds new UUID PK columns, populates from mapping
- Adds new UUID FK columns, populates from mapping
- Drops old PK/FK columns, renames new ones
- Re-adds FK constraints

### Files Modified
- 11 entity classes: id type Long→UUID, `GenerationType.IDENTITY`→`GenerationType.UUID`
- 11 repository interfaces: `JpaRepository<X, Long>`→`JpaRepository<X, UUID>`
- 9 DTOs: `Long id`→`UUID id`
- All service interfaces and implementations: parameter/return types Long→UUID
- All controllers: path variable types Long→UUID
- 2 mappers: no changes needed (MapStruct auto-maps UUID id)

---

## Part 2: Default Subject

### Entity Change (Subject.java)
Added `defaultSubject` boolean flag:
```java
@Column(name = "default_subject", nullable = false)
@Builder.Default
private Boolean defaultSubject = false;
```

### Migration (V12__add_default_subject.sql)
```sql
ALTER TABLE subject ADD COLUMN default_subject BOOLEAN NOT NULL DEFAULT FALSE;
```

### Auto-Creation
`SemesterServiceImpl.createSemester()` now auto-creates one Subject with:
- `name = "General"`
- `defaultSubject = true`

### Validation
`SubjectServiceImpl.deleteSubject()` rejects deletion of default subjects:
```java
if (Boolean.TRUE.equals(subject.getDefaultSubject())) {
    throw new RuntimeException("Cannot delete the default subject");
}
```

### SubjectResponse
Exposes `defaultSubject` for the frontend to display badge/icon and disable rename/delete.

---

## Hierarchy (unchanged)

```
Semester
    ↓
Subject (General is a normal Subject with defaultSubject=true)
    ↓
Folder
    ↓
Document
```

Default Subject behaves exactly like any other Subject. No special folder or upload logic.
