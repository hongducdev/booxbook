package com.booxbook.core.engine.epub

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to retrieve assets and open publications using Readium Kotlin Toolkit 3.x.
 */
@Singleton
class ReadiumAssetRetriever @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    private val publicationParser = DefaultPublicationParser(
        context = context,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )

    private val publicationOpener = PublicationOpener(
        publicationParser = publicationParser
    )

    /**
     * Retrieves an asset from a local file.
     */
    suspend fun retrieveAsset(file: File): Result<Asset> {
        val result = assetRetriever.retrieve(file)
        val asset = result.getOrNull()
        return if (asset != null) {
            Result.success(asset)
        } else {
            Result.failure(Exception("Could not retrieve asset: ${result.failureOrNull()}"))
        }
    }

    /**
     * Opens a publication from a local file.
     */
    suspend fun openPublication(file: File): Result<Publication> {
        val assetResult = retrieveAsset(file)
        if (assetResult.isFailure) {
            return Result.failure(assetResult.exceptionOrNull() ?: Exception("Failed to retrieve asset"))
        }
        val asset = assetResult.getOrThrow()
        val openResult = publicationOpener.open(
            asset = asset,
            allowUserInteraction = false
        )
        val publication = openResult.getOrNull()
        return if (publication != null) {
            Result.success(publication)
        } else {
            Result.failure(Exception("Could not open publication: ${openResult.failureOrNull()}"))
        }
    }
}
