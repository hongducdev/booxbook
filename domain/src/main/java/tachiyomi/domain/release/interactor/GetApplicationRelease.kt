package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isNightly,
            arguments.commitCount,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isNightly: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        return if (isNightly) {
            versionTag.removePrefix("r").toIntOrNull()?.let { it > commitCount } ?: false
        } else {
            val newVersion = parseStableVersion(versionTag) ?: return false
            val currentVersion = parseStableVersion(versionName) ?: return false
            val componentCount = maxOf(newVersion.size, currentVersion.size)

            repeat(componentCount) { index ->
                val comparison = newVersion.getOrElse(index) { 0 }
                    .compareTo(currentVersion.getOrElse(index) { 0 })
                if (comparison != 0) {
                    return comparison > 0
                }
            }

            false
        }
    }

    private fun parseStableVersion(value: String): List<Int>? {
        val normalized = value.removePrefix("v")
        if (!normalized.matches(STABLE_VERSION_PATTERN)) return null
        return normalized.split(".").map { it.toIntOrNull() ?: return null }
    }

    companion object {
        private val STABLE_VERSION_PATTERN = Regex("""\d+(?:\.\d+)*""")
    }

    data class Arguments(
        val isNightly: Boolean,
        val commitCount: Int,
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}
