package com.booxbook.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Nâng cấp schema 2 → 3: metadata sách đọc từ OPF, và bảng đánh giá sau khi đọc.
 *
 * Viết tay chứ không bump version trần. `DatabaseModule` trước đây dùng
 * `fallbackToDestructiveMigration()`, nghĩa là mọi thay đổi schema đều **xoá sạch thư viện, tiến độ đọc và
 * toàn bộ thống kê** của người dùng — không phải rủi ro lý thuyết mà là hành vi mặc định.
 *
 * Ba lưu ý khiến migration này dễ viết sai, và cách xử lý:
 *
 * 1. **SQLite không cho thêm cột `NOT NULL` mà không có `DEFAULT`.** Vì vậy `tags` phải có `DEFAULT ''`, và
 *    tương ứng entity phải khai `@ColumnInfo(defaultValue = "")`. Room so khớp **giá trị mặc định** giữa
 *    schema mong đợi và schema thật; lệch một bên là `IllegalStateException` ngay lần mở DB đầu tiên.
 * 2. **`book_reviews` không được có `DEFAULT`.** Entity dùng giá trị mặc định của Kotlin, không phải của SQL,
 *    nên `CREATE TABLE` phải khớp đúng thứ Room sinh ra — kể cả mệnh đề `ON UPDATE NO ACTION`.
 * 3. **`book_id` là khoá chính nên đã được đánh chỉ mục**, không tạo thêm index — Room sẽ báo thừa.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `series` TEXT")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `series_index` TEXT")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `description` TEXT")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `language` TEXT")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `book_reviews` (
                `book_id` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `review` TEXT NOT NULL,
                `updated_timestamp` INTEGER NOT NULL,
                `finished_at` INTEGER,
                PRIMARY KEY(`book_id`),
                FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

/** Toàn bộ migration đã có, theo thứ tự. Truyền cho `RoomDatabase.Builder.addMigrations`. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_2_3)
