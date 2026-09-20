package com.booxbook.core.database.repository

import android.net.Uri
import com.booxbook.core.database.dao.AnnotationDao
import com.booxbook.core.database.dao.BookDao
import com.booxbook.core.database.dao.BookReviewDao
import com.booxbook.core.database.dao.ReadingProgressDao
import com.booxbook.core.database.dao.ReadingSessionDao
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.database.entity.ReadingSessionEntity
import com.booxbook.core.database.entity.asDomainModel
import com.booxbook.core.database.entity.asEntity
import com.booxbook.core.database.entity.encodeTags
import com.booxbook.core.database.storage.BookStorageManager
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.BookReadingStat
import com.booxbook.core.model.BookReview
import com.booxbook.core.model.DailyReadingStat
import com.booxbook.core.model.HeatmapDayStat
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val annotationDao: AnnotationDao,
    private val readingSessionDao: ReadingSessionDao,
    private val bookReviewDao: BookReviewDao,
    private val storageManager: BookStorageManager
) : BookRepository {

    private val _dailyGoalMinutes = MutableStateFlow(45)

    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getAllBooksWithProgress(): Flow<List<BookWithProgress>> {
        return bookDao.getAllBooksWithProgress().map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getRecentBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getRecentBooks(limit).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getRecentBooksWithProgress(limit: Int): Flow<List<BookWithProgress>> {
        return bookDao.getRecentBooksWithProgress(limit).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getBooksByFormat(format: BookFormat): Flow<List<Book>> {
        return bookDao.getBooksByFormat(format).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getBookById(id: String): Flow<Book?> {
        return bookDao.getBookById(id).map { it?.asDomainModel() }
    }

    override suspend fun getBookByIdSync(id: String): Book? {
        return bookDao.getBookByIdSync(id)?.asDomainModel()
    }

    override suspend fun importBookFromUri(uri: Uri): Result<Book> {
        return storageManager.importBookFromUri(uri).mapCatching { info ->
            val book = Book(
                id = info.id,
                title = info.title,
                author = info.author,
                filePath = info.filePath,
                coverPath = info.coverPath,
                format = info.format,
                fileSize = info.fileSize,
                addedTimestamp = System.currentTimeMillis(),
                series = info.series,
                seriesIndex = info.seriesIndex,
                tags = info.tags,
                description = info.description,
                language = info.language
            )
            bookDao.insertBook(book.asEntity())
            book
        }
    }

    override suspend fun saveBook(book: Book) {
        bookDao.upsertBook(book.asEntity())
    }

    override fun getReview(bookId: String): Flow<BookReview?> =
        bookReviewDao.getReview(bookId).map { it?.asDomainModel() }

    override suspend fun getReviewSync(bookId: String): BookReview? =
        bookReviewDao.getReviewSync(bookId)?.asDomainModel()

    override fun getAllReviews(): Flow<List<BookReview>> =
        bookReviewDao.getAllReviews().map { entities -> entities.map { it.asDomainModel() } }

    override suspend fun saveReview(review: BookReview) {
        // Mốc cập nhật do tầng lưu trữ đặt, không phải nơi gọi: nó phải là thời điểm ghi thật.
        bookReviewDao.upsertReview(
            review.copy(updatedTimestamp = System.currentTimeMillis()).asEntity()
        )
    }

    override suspend fun deleteReview(bookId: String) = bookReviewDao.deleteReview(bookId)

    override suspend fun backfillMetadata(): Int {
        val candidates = bookDao.getBooksMissingMetadata()
        candidates.forEach { entity ->
            val extracted = storageManager.reExtractMetadata(entity.asDomainModel())
            // Ghi `""` chứ không để `null`: `null` nghĩa là "chưa quét", nên để nguyên sẽ khiến lần mở ứng dụng
            // sau quét lại đúng những tệp này — kể cả CBZ/AZW3 vốn không có metadata văn bản để đọc.
            bookDao.updateBook(
                entity.copy(
                    series = extracted?.series.orEmpty(),
                    seriesIndex = extracted?.seriesIndex.orEmpty(),
                    tags = encodeTags(extracted?.tags.orEmpty()),
                    description = extracted?.description.orEmpty(),
                    language = extracted?.language.orEmpty()
                )
            )
        }
        return candidates.size
    }

    override suspend fun updateLastRead(bookId: String, timestamp: Long) {
        bookDao.updateLastReadTimestamp(bookId, timestamp)
    }

    override suspend fun deleteBook(id: String) {
        val existing = bookDao.getBookByIdSync(id)
        if (existing != null) {
            storageManager.deleteBookFiles(existing.filePath, existing.coverPath)
            bookDao.deleteBookById(id)
        }
    }

    override fun getReadingProgress(bookId: String): Flow<ReadingProgress?> {
        return readingProgressDao.getProgressForBook(bookId).map { it?.asDomainModel() }
    }

    override suspend fun getReadingProgressSync(bookId: String): ReadingProgress? {
        return readingProgressDao.getProgressForBookSync(bookId)?.asDomainModel()
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        readingProgressDao.upsertProgress(progress.asEntity())
        bookDao.updateLastReadTimestamp(progress.bookId, progress.updatedTimestamp)
    }

    override suspend fun deleteReadingProgress(bookId: String) {
        readingProgressDao.deleteProgressForBook(bookId)
    }

    override fun getAnnotationsForBook(bookId: String): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsForBook(bookId).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsByType(bookId, type).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override suspend fun addAnnotation(annotation: Annotation): Long {
        return annotationDao.insertAnnotation(annotation.asEntity())
    }

    override suspend fun updateAnnotation(annotation: Annotation) {
        annotationDao.updateAnnotation(annotation.asEntity())
    }

    override suspend fun removeAnnotation(id: Long) {
        annotationDao.deleteAnnotationById(id)
    }

    override suspend fun recordReadingSession(
        bookId: String,
        startTime: Long,
        endTime: Long,
        durationSeconds: Long
    ): Long {
        val actualDuration = if (durationSeconds > 0) durationSeconds else maxOf(1L, (endTime - startTime) / 1000)
        val todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entity = ReadingSessionEntity(
            bookId = bookId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = actualDuration,
            date = todayDate
        )
        val insertedId = readingSessionDao.insertSession(entity)
        bookDao.updateLastReadTimestamp(bookId, endTime)
        return insertedId
    }

    override fun getAllReadingSessions(): Flow<List<ReadingSession>> {
        return readingSessionDao.getAllSessions().map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getDailyGoalMinutes(): Flow<Int> = _dailyGoalMinutes.asStateFlow()

    override suspend fun setDailyGoalMinutes(minutes: Int) {
        _dailyGoalMinutes.value = minutes
    }

    override fun getReadingStatisticsOverview(): Flow<ReadingStatisticsOverview> {
        return combine(
            readingSessionDao.getAllSessions(),
            readingSessionDao.getDistinctReadingDates(),
            bookDao.getAllBooksWithProgress(),
            _dailyGoalMinutes
        ) { sessions, distinctDates, booksWithProgress, goalMinutes ->
            val todayDate = LocalDate.now()
            val todayStr = todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val todayDurationSecs = sessions.filter { it.date == todayStr }.sumOf { it.durationSeconds }
            val todayMinutes = (todayDurationSecs / 60).toInt()

            val totalSecs = sessions.sumOf { it.durationSeconds }
            val totalHours = ((totalSecs / 3600f) * 10).roundToInt() / 10f

            val completedBooksCount = booksWithProgress.count { (it.progress?.percentage ?: 0f) >= 0.99f }

            // Streak calculation
            val parsedDates = distinctDates.mapNotNull {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }.sortedDescending()
            val dateSet = parsedDates.toSet()

            var currentStreak = 0
            val yesterday = todayDate.minusDays(1)
            val startStreakDate = when {
                dateSet.contains(todayDate) -> todayDate
                dateSet.contains(yesterday) -> yesterday
                else -> null
            }

            if (startStreakDate != null) {
                var checkDate: LocalDate = startStreakDate
                while (dateSet.contains(checkDate)) {
                    currentStreak++
                    checkDate = checkDate.minusDays(1)
                }
            }

            var longestStreak = 0
            if (parsedDates.isNotEmpty()) {
                val ascendingDates = parsedDates.sorted()
                var tempStreak = 1
                longestStreak = 1
                for (i in 1 until ascendingDates.size) {
                    if (ascendingDates[i] == ascendingDates[i - 1].plusDays(1)) {
                        tempStreak++
                        longestStreak = maxOf(longestStreak, tempStreak)
                    } else if (ascendingDates[i] != ascendingDates[i - 1]) {
                        tempStreak = 1
                    }
                }
            }

            // 7-day weekly stats
            val weeklyStats = (6 downTo 0).map { daysAgo ->
                val targetDate = todayDate.minusDays(daysAgo.toLong())
                val targetDateStr = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val dayLabel = when (targetDate.dayOfWeek) {
                    DayOfWeek.MONDAY -> "T2"
                    DayOfWeek.TUESDAY -> "T3"
                    DayOfWeek.WEDNESDAY -> "T4"
                    DayOfWeek.THURSDAY -> "T5"
                    DayOfWeek.FRIDAY -> "T6"
                    DayOfWeek.SATURDAY -> "T7"
                    DayOfWeek.SUNDAY -> "CN"
                }
                val minutesInDay = (sessions.filter { it.date == targetDateStr }.sumOf { it.durationSeconds } / 60).toInt()
                DailyReadingStat(
                    date = targetDateStr,
                    dayOfWeek = dayLabel,
                    durationMinutes = minutesInDay,
                    isToday = daysAgo == 0
                )
            }

            // 16-week Activity Heatmap stats (16 weeks * 7 days = 112 days)
            val currentMonday = todayDate.with(DayOfWeek.MONDAY)
            val startMonday = currentMonday.minusWeeks(15) // 16 weeks total
            val totalDays = 16 * 7

            val heatmapStats = (0 until totalDays).map { dayOffset ->
                val day = startMonday.plusDays(dayOffset.toLong())
                val dayStr = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val minutesInDay = (sessions.filter { it.date == dayStr }.sumOf { it.durationSeconds } / 60).toInt()
                val level = when {
                    minutesInDay == 0 -> 0
                    minutesInDay <= 15 -> 1
                    minutesInDay <= 30 -> 2
                    minutesInDay <= 60 -> 3
                    else -> 4
                }
                HeatmapDayStat(
                    date = dayStr,
                    durationMinutes = minutesInDay,
                    level = level,
                    dayOfWeek = day.dayOfWeek.value, // 1 = Monday .. 7 = Sunday
                    isToday = day == todayDate
                )
            }

            // Breakdown by book
            val durationByBook = sessions.groupBy { it.bookId }
                .mapValues { entry -> entry.value.sumOf { it.durationSeconds } }

            val topBooks = durationByBook.mapNotNull { (bookId, totalDuration) ->
                val bwp = booksWithProgress.firstOrNull { it.book.id == bookId }
                bwp?.let {
                    BookReadingStat(
                        book = it.book.asDomainModel(),
                        totalDurationSeconds = totalDuration,
                        progressPercentage = it.progress?.percentage ?: 0f
                    )
                }
            }.sortedByDescending { it.totalDurationSeconds }

            ReadingStatisticsOverview(
                todayMinutes = todayMinutes,
                dailyGoalMinutes = goalMinutes,
                totalReadingHours = totalHours,
                totalSessionsCount = sessions.size,
                currentStreakDays = currentStreak,
                longestStreakDays = longestStreak,
                completedBooksCount = completedBooksCount,
                weeklyStats = weeklyStats,
                heatmapStats = heatmapStats,
                topBooks = topBooks
            )
        }
    }
}
