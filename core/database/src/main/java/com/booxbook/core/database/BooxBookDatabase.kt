package com.booxbook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.booxbook.core.database.converter.Converters
import com.booxbook.core.database.dao.AnnotationDao
import com.booxbook.core.database.dao.BookDao
import com.booxbook.core.database.dao.BookReviewDao
import com.booxbook.core.database.dao.ReadingProgressDao
import com.booxbook.core.database.dao.ReadingSessionDao
import com.booxbook.core.database.entity.AnnotationEntity
import com.booxbook.core.database.entity.BookEntity
import com.booxbook.core.database.entity.BookReviewEntity
import com.booxbook.core.database.entity.ReadingProgressEntity
import com.booxbook.core.database.entity.ReadingSessionEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        AnnotationEntity::class,
        ReadingSessionEntity::class,
        BookReviewEntity::class
    ],
    version = DatabaseConstants.DATABASE_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BooxBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun bookReviewDao(): BookReviewDao
}
