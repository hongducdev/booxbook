package com.booxbook.core.database

/**
 * Placeholder for Core Database module.
 * Full Room Database, Entities, and DAOs are implemented in Phase 2.
 */
object DatabaseConstants {
    const val DATABASE_NAME = "booxbook.db"

    /**
     * Phiên bản schema. Tăng số này **bắt buộc** đi kèm một `Migration` trong [ALL_MIGRATIONS].
     *
     * 2 → 3: thêm metadata sách (`series`/`series_index`/`tags`/`description`/`language`) và bảng `book_reviews`.
     */
    const val DATABASE_VERSION = 3
}
