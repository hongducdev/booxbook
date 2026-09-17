package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "EPUB"),
    AZW3("azw3", "Kindle AZW3"),
    CBZ("cbz", "Comic CBZ");

    companion object {
        fun fromExtension(ext: String?): BookFormat? {
            val cleanExt = ext?.lowercase()?.trimStart('.') ?: return null
            return entries.firstOrNull { it.extension == cleanExt }
        }
    }
}
