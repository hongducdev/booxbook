package com.booxbook.core.engine.di

import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.azw3.Azw3ReaderEngine
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
        azw3Engine: Azw3ReaderEngine,
        cbzEngine: CbzReaderEngine
    ): Map<BookFormat, @JvmSuppressWildcards ReaderEngine> {
        return mapOf(
            BookFormat.EPUB to epubEngine,
            BookFormat.AZW3 to azw3Engine,
            BookFormat.CBZ to cbzEngine
        )
    }
}
