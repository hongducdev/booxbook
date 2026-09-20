package com.booxbook.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.entity.BookReviewEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kiểm chứng `MIGRATION_2_3` trên một DB version 2 **dựng tay**.
 *
 * Migration là chỗ nguy hiểm nhất của thay đổi này: viết sai thì người dùng mất thư viện, tiến độ đọc và toàn
 * bộ thống kê, và họ chỉ biết khi đã quá muộn. Đã kiểm một lần trên DB thật của người dùng, nhưng kiểm tay
 * không ngăn được lần sau làm hỏng — nên phải có test.
 *
 * Cách làm: dựng DB v2 bằng DDL thô + chèn dữ liệu, rồi mở chính tệp đó bằng Room có gắn migration. Room
 * **tự kiểm tra schema sau migration** đối chiếu với entity, nên nếu migration thiếu cột, sai kiểu, hay thiếu
 * bảng thì test này đỏ ngay — không cần tự viết phần so khớp schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var databaseFile: File

    private val bookId = "book-cu"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = File(context.cacheDir, "migration-test.db")
        databaseFile.delete()
        databaseFile.parentFile?.mkdirs()
        createVersion2Database()
    }

    @After
    fun tearDown() {
        databaseFile.delete()
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
    }

    /**
     * DDL của schema version 2 — đúng những gì Room sinh ra trước khi thêm metadata và bảng đánh giá.
     *
     * Viết tay vì schema của v2 chưa từng được export; đây là bản chép lại từ định nghĩa entity ở thời điểm đó.
     */
    private fun createVersion2Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`author` TEXT NOT NULL, `file_path` TEXT NOT NULL, `cover_path` TEXT, " +
                    "`format` TEXT NOT NULL, `total_pages` INTEGER NOT NULL, `file_size` INTEGER NOT NULL, " +
                    "`added_timestamp` INTEGER NOT NULL, `last_read_timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_last_read_timestamp` ON `books` (`last_read_timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_format` ON `books` (`format`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_progress` (`book_id` TEXT NOT NULL, " +
                    "`locator` TEXT NOT NULL, `percentage` REAL NOT NULL, `current_page` INTEGER NOT NULL, " +
                    "`total_pages` INTEGER NOT NULL, `updated_timestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`book_id`), FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_progress_book_id` ON `reading_progress` (`book_id`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `annotations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`book_id` TEXT NOT NULL, `type` TEXT NOT NULL, `locator` TEXT NOT NULL, " +
                    "`selected_text` TEXT, `note_content` TEXT, `color_hex` TEXT NOT NULL, " +
                    "`created_timestamp` INTEGER NOT NULL, FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_book_id` ON `annotations` (`book_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_book_id_type` ON `annotations` (`book_id`, `type`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`book_id` TEXT NOT NULL, `start_time` INTEGER NOT NULL, `end_time` INTEGER NOT NULL, " +
                    "`duration_seconds` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                    "FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_book_id` ON `reading_sessions` (`book_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_date` ON `reading_sessions` (`date`)")

            db.execSQL(
                "INSERT INTO books (id, title, author, file_path, cover_path, format, total_pages, " +
                    "file_size, added_timestamp, last_read_timestamp) VALUES " +
                    "('$bookId', 'Tù Nhân', 'Freida McFadden', '/data/books/cu.epub', '/data/covers/cu.jpg', " +
                    "'EPUB', 297, 500000, 1700000000000, 1700000900000)"
            )
            db.execSQL(
                "INSERT INTO reading_progress (book_id, locator, percentage, current_page, total_pages, " +
                    "updated_timestamp) VALUES ('$bookId', 'epubcfi(/6/4)', 0.037, 11, 297, 1700000900000)"
            )
            db.execSQL(
                "INSERT INTO annotations (book_id, type, locator, selected_text, note_content, color_hex, " +
                    "created_timestamp) VALUES ('$bookId', 'BOOKMARK', 'epubcfi(/6/8)', NULL, 'Trang 12', " +
                    "'#FFEB3B', 1700000800000)"
            )
            db.execSQL(
                "INSERT INTO reading_sessions (book_id, start_time, end_time, duration_seconds, date) " +
                    "VALUES ('$bookId', 1700000000000, 1700000600000, 600, '2026-09-18')"
            )

            db.version = 2
        } finally {
            db.close()
        }
    }

    /** Mở DB cũ bằng Room có migration; việc mở chính là lúc migration chạy và schema được kiểm tra. */
    private fun openMigratedDatabase(): BooxBookDatabase =
        Room.databaseBuilder(context, BooxBookDatabase::class.java, databaseFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migration len 3 giu nguyen sach tien do phien doc va chu thich`() = runBlocking {
        val db = openMigratedDatabase()
        try {
            // Chạm vào DB để migration thực sự chạy.
            val book = db.bookDao().getBookByIdSync(bookId)
            assertNotNull("Sách cũ bị mất sau migration", book)
            assertEquals("Tù Nhân", book!!.title)
            assertEquals("Freida McFadden", book.author)
            assertEquals(297, book.totalPages)
            assertEquals(500_000L, book.fileSize)

            val progress = db.readingProgressDao().getProgressForBookSync(bookId)
            assertNotNull("Tiến độ đọc bị mất sau migration", progress)
            assertEquals(0.037f, progress!!.percentage, 1e-6f)
            assertEquals(11, progress.currentPage)

            assertEquals(1, db.annotationDao().getAnnotationsForBookSync(bookId).size)
            assertEquals(1, db.readingSessionDao().getSessionsForBookSync(bookId).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `cot metadata moi ton tai va o trang thai chua quet`() = runBlocking {
        val db = openMigratedDatabase()
        try {
            val book = db.bookDao().getBookByIdSync(bookId)!!

            // `null` = chưa quét, khác `""` = đã quét mà sách không khai báo. `backfillMetadata` dựa vào sự
            // khác biệt này để biết bản ghi nào cần mở lại tệp.
            assertNull(book.series)
            assertNull(book.description)
            assertNull(book.language)
            assertEquals("", book.tags)
        } finally {
            db.close()
        }
    }

    @Test
    fun `bang danh gia duoc tao rong va dung duoc ngay sau migration`() = runBlocking {
        val db = openMigratedDatabase()
        try {
            assertNull(db.bookReviewDao().getReviewSync(bookId))

            db.bookReviewDao().upsertReview(
                BookReviewEntity(
                    bookId = bookId,
                    rating = 4,
                    review = "Hay",
                    updatedTimestamp = 1700001000000L,
                    finishedAt = null
                )
            )

            val saved = db.bookReviewDao().getReviewSync(bookId)
            assertNotNull(saved)
            assertEquals(4, saved!!.rating)
            assertEquals("Hay", saved.review)
        } finally {
            db.close()
        }
    }

    @Test
    fun `xoa sach thi danh gia bi xoa theo qua khoa ngoai cascade`() = runBlocking {
        val db = openMigratedDatabase()
        try {
            db.bookReviewDao().upsertReview(BookReviewEntity(bookId = bookId, rating = 5))
            db.bookDao().deleteBookById(bookId)

            // Khoá ngoại CASCADE phải được khai trong CREATE TABLE của migration, nếu không đánh giá sẽ mồ côi.
            assertNull(db.bookReviewDao().getReviewSync(bookId))
        } finally {
            db.close()
        }
    }

    @Test
    fun `chi sach chua quet moi lot vao danh sach quet lai`() = runBlocking {
        val db = openMigratedDatabase()
        try {
            val before = db.bookDao().getBooksMissingMetadata()
            assertEquals(1, before.size)

            // Giả lập kết quả quét: ghi `""` cho cả ba cột.
            db.bookDao().updateBook(before.single().copy(series = "", description = "", language = ""))

            assertTrue(
                "Sách đã quét vẫn lọt vào danh sách — mỗi lần mở ứng dụng sẽ mở lại từng tệp EPUB",
                db.bookDao().getBooksMissingMetadata().isEmpty()
            )
        } finally {
            db.close()
        }
    }
}
