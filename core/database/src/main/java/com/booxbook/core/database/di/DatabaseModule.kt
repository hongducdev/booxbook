package com.booxbook.core.database.di

import android.content.Context
import androidx.room.Room
import com.booxbook.core.database.ALL_MIGRATIONS
import com.booxbook.core.database.BooxBookDatabase
import com.booxbook.core.database.DatabaseConstants
import com.booxbook.core.database.dao.AnnotationDao
import com.booxbook.core.database.dao.BookDao
import com.booxbook.core.database.dao.BookReviewDao
import com.booxbook.core.database.dao.ReadingProgressDao
import com.booxbook.core.database.dao.ReadingSessionDao
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.database.repository.BookRepositoryImpl
import com.booxbook.core.database.storage.BookStorageManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBooxBookDatabase(
        @ApplicationContext context: Context
    ): BooxBookDatabase {
        return Room.databaseBuilder(
            context,
            BooxBookDatabase::class.java,
            DatabaseConstants.DATABASE_NAME
        )
            // Cố ý **không** dùng `fallbackToDestructiveMigration()`: nó biến mọi lần tăng version schema
            // thành một lần xoá sạch thư viện, tiến độ đọc và thống kê của người dùng. Mỗi lần tăng version
            // phải đi kèm một `Migration` viết tay trong `ALL_MIGRATIONS`.
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    fun provideBookDao(database: BooxBookDatabase): BookDao = database.bookDao()

    @Provides
    fun provideReadingProgressDao(database: BooxBookDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideAnnotationDao(database: BooxBookDatabase): AnnotationDao =
        database.annotationDao()

    @Provides
    fun provideReadingSessionDao(database: BooxBookDatabase): ReadingSessionDao =
        database.readingSessionDao()

    @Provides
    fun provideBookReviewDao(database: BooxBookDatabase): BookReviewDao =
        database.bookReviewDao()

    @Provides
    @Singleton
    fun provideBookStorageManager(
        @ApplicationContext context: Context
    ): BookStorageManager = BookStorageManager(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository
}
