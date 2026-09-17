package com.booxbook.core.engine.di

import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.cbz.CbzReaderEngine
import com.booxbook.core.engine.epub.EpubReaderEngine
import com.booxbook.core.model.BookFormat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideEngineMap(
        epubEngine: EpubReaderEngine,
        cbzEngine: CbzReaderEngine
    ): Map<BookFormat, @JvmSuppressWildcards ReaderEngine> {
        return mapOf(
            BookFormat.EPUB to epubEngine,
            BookFormat.CBZ to cbzEngine
        )
    }
}
