# Journal Entry: Phase 2 - Core Database & Storage Layer Implementation
**Date:** 2026-09-17  
**Session:** Phase 2 Implementation & Verification  
**Module:** `:core:database`  

## Key Decisions & Architecture
- **Room SQLite Entities & Cascading Delete:**
  - `BookEntity`: Master entity storing metadata (id, title, author, internal file path, cover image path, format, totalPages, fileSize, timestamps). Indexed on `last_read_timestamp` and `format`.
  - `ReadingProgressEntity`: Stores locator (Readium CFI string for EPUB/AZW3 or page index for CBZ), completion percentage, and current page. Linked via foreign key with `CASCADE` delete to `BookEntity`.
  - `AnnotationEntity`: Stores Highlights, Notes, and Bookmarks with auto-increment ID and `CASCADE` foreign key.
- **Storage Access Framework (SAF) & BookStorageManager:**
  - Designed `BookStorageManager` to safely stream files from Android `content://` and `file://` URIs into app-internal directory `files/books/`.
  - Built metadata & cover extraction engine:
    - **EPUB:** Inspects `META-INF/container.xml`, parses the OPF manifest to extract `<dc:title>`, `<dc:creator>`, and extracts the cover image into `files/covers/`.
    - **CBZ:** Inspects the ZIP archive and extracts the first image entry (alphabetically sorted) into `files/covers/`.
  - Integrated full file lifecycle cleanup (`deleteBookFiles`) upon book deletion.
- **Repository Pattern & Hilt DI:**
  - `BookRepository` interface and `BookRepositoryImpl` provide a reactive Single Source of Truth via Kotlin Flow.
  - Automatically updates `last_read_timestamp` on the book entity whenever reading progress is recorded.
  - Hilt `DatabaseModule` configured to provide Singleton database, DAOs, storage manager, and repository binding.

## Testing & Verification
- Implemented in-memory Room database unit tests via Robolectric and `kotlinx-coroutines-test`:
  - `BooxBookDaoTest`: 8 test cases verifying CRUD, sorting, format filtering, reading progress upsert, and foreign key cascade deletion.
  - `BookRepositoryTest`: 4 test cases validating domain mappers, repository operations, and reading progress timestamp propagation.
  - `BookStorageManagerTest`: 2 test cases verifying CBZ first-image cover extraction and EPUB container/OPF parsing and cover extraction.
- **Results:** 14/14 test cases passed (100% success rate), clean build across all project modules.
