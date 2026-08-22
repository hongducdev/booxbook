package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibrarySortModeTest {
    @Test
    fun `removed tracker score sort falls back to alphabetical`() {
        LibrarySort.deserialize("TRACKER_MEAN,DESCENDING") shouldBe LibrarySort(
            LibrarySort.Type.Alphabetical,
            LibrarySort.Direction.Descending,
        )
        LibrarySort.valueOf(0b00100000) shouldBe LibrarySort.default.copy(
            direction = LibrarySort.Direction.Descending,
        )
    }
}
