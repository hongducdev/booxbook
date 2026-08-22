package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService

    @BeforeEach
    fun beforeEach() {
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService)
    }

    @Test
    fun `When has update but is preview expect new update`() = runTest {
        val release = Release(
            "r2000",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isNightly = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has update expect new update`() = runTest {
        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isNightly = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isNightly = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `Stable update compares patch versions`() = runTest {
        stableResult(currentVersion = "0.0.1", releaseVersion = "v0.0.2")
            .let { it is GetApplicationRelease.Result.NewUpdate } shouldBe true
    }

    @Test
    fun `Stable update stops at first lower component`() = runTest {
        stableResult(currentVersion = "1.0.0", releaseVersion = "v0.1.0") shouldBe
            GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `Stable update normalizes missing trailing components`() = runTest {
        stableResult(currentVersion = "1.0.0", releaseVersion = "v1.0") shouldBe
            GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `Stable update rejects malformed release tags`() = runTest {
        stableResult(currentVersion = "0.0.1", releaseVersion = "latest") shouldBe
            GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `Version 0_0_1 is not newer than itself`() = runTest {
        stableResult(currentVersion = "0.0.1", releaseVersion = "v0.0.1") shouldBe
            GetApplicationRelease.Result.NoNewUpdate
    }

    private suspend fun stableResult(
        currentVersion: String,
        releaseVersion: String,
    ): GetApplicationRelease.Result {
        coEvery { releaseService.latest(any()) } returns Release(
            releaseVersion,
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        return getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isNightly = false,
                commitCount = 0,
                versionName = currentVersion,
                repository = "test",
            ),
        )
    }
}
