package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "type"])
    ]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "type")
    val type: AnnotationType,
    @ColumnInfo(name = "locator")
    val locator: String,
    @ColumnInfo(name = "selected_text")
    val selectedText: String? = null,
    @ColumnInfo(name = "note_content")
    val noteContent: String? = null,
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#FFEB3B",
    @ColumnInfo(name = "created_timestamp")
    val createdTimestamp: Long = System.currentTimeMillis()
)

fun AnnotationEntity.asDomainModel(): Annotation = Annotation(
    id = id,
    bookId = bookId,
    type = type,
    locator = locator,
    selectedText = selectedText,
    noteContent = noteContent,
    colorHex = colorHex,
    createdTimestamp = createdTimestamp
)

fun Annotation.asEntity(): AnnotationEntity = AnnotationEntity(
    id = id,
    bookId = bookId,
    type = type,
    locator = locator,
    selectedText = selectedText,
    noteContent = noteContent,
    colorHex = colorHex,
    createdTimestamp = createdTimestamp
)
