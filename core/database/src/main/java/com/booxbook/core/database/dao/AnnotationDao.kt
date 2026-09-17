package com.booxbook.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.booxbook.core.database.entity.AnnotationEntity
import com.booxbook.core.model.AnnotationType
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE book_id = :bookId ORDER BY created_timestamp DESC")
    fun getAnnotationsForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE book_id = :bookId AND type = :type ORDER BY created_timestamp DESC")
    fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)

    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Long)
}
