package com.booxbook.feature.reader

import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Màn đọc chỉ nói chuyện với snackbar qua [ReaderFeedback.toSnackbar], nên câu chữ và nút hành động
 * được khoá lại bằng test thay vì kiểm tra bằng mắt trên thiết bị.
 */
class ReaderFeedbackTest {

    private fun annotation(type: AnnotationType) = Annotation(
        id = 7L,
        bookId = "book-1",
        type = type,
        locator = "page://3",
        noteContent = "Trang 4"
    )

    @Test
    fun `bookmark feedback describes both directions`() {
        assertEquals("Đã đánh dấu trang", ReaderFeedback.BookmarkAdded.toSnackbar().message)
        assertEquals("Đã bỏ đánh dấu trang", ReaderFeedback.BookmarkRemoved.toSnackbar().message)
    }

    @Test
    fun `tts feedback distinguishes a stop from finishing the book`() {
        assertEquals("Đang đọc bằng giọng nói", ReaderFeedback.TtsStarted.toSnackbar().message)
        assertEquals("Đã dừng đọc bằng giọng nói", ReaderFeedback.TtsStopped.toSnackbar().message)
        assertEquals("Đã đọc hết cuốn sách", ReaderFeedback.BookFinished.toSnackbar().message)
    }

    @Test
    fun `deleting a bookmark offers undo carrying the deleted record`() {
        val bookmark = annotation(AnnotationType.BOOKMARK)

        val snackbar = ReaderFeedback.AnnotationDeleted(bookmark).toSnackbar()

        assertEquals("Đã xoá đánh dấu trang", snackbar.message)
        assertEquals(READER_UNDO_LABEL, snackbar.actionLabel)
        assertSame(bookmark, snackbar.undoAnnotation)
    }

    @Test
    fun `deleting a note is worded as a note and offers undo`() {
        val note = annotation(AnnotationType.NOTE)

        val snackbar = ReaderFeedback.AnnotationDeleted(note).toSnackbar()

        assertEquals("Đã xoá ghi chú", snackbar.message)
        assertEquals(READER_UNDO_LABEL, snackbar.actionLabel)
    }

    @Test
    fun `restored feedback is informational and never offers a second undo`() {
        val snackbar = ReaderFeedback.AnnotationRestored(annotation(AnnotationType.BOOKMARK)).toSnackbar()

        assertEquals("Đã khôi phục đánh dấu trang", snackbar.message)
        assertNull(snackbar.actionLabel)
        assertNull(snackbar.undoAnnotation)
    }

    @Test
    fun `failure feedback shows the underlying message verbatim`() {
        val snackbar = ReaderFeedback.Failure("Không thể xoá ghi chú: đĩa đầy").toSnackbar()

        assertEquals("Không thể xoá ghi chú: đĩa đầy", snackbar.message)
        assertNull(snackbar.actionLabel)
    }
}
