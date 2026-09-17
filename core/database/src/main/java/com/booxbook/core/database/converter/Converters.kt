package com.booxbook.core.database.converter

import androidx.room.TypeConverter
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.BookFormat

class Converters {
    @TypeConverter
    fun fromBookFormat(value: BookFormat): String {
        return value.name
    }

    @TypeConverter
    fun toBookFormat(value: String): BookFormat {
        return runCatching { BookFormat.valueOf(value) }
            .getOrDefault(BookFormat.EPUB)
    }

    @TypeConverter
    fun fromAnnotationType(value: AnnotationType): String {
        return value.name
    }

    @TypeConverter
    fun toAnnotationType(value: String): AnnotationType {
        return runCatching { AnnotationType.valueOf(value) }
            .getOrDefault(AnnotationType.NOTE)
    }
}
