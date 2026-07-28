package de.kruemmel.rufwaechter.reputation

import de.kruemmel.rufwaechter.importexport.ImportExportManager
import de.kruemmel.rufwaechter.importexport.ReputationParseOutcome
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed interface ReputationRefreshResult {
    data class Updated(val accepted: Int, val rejected: Int, val sha256: String) : ReputationRefreshResult
    data class Failed(val message: String) : ReputationRefreshResult
    data object Offline : ReputationRefreshResult
}

interface ReputationSourceProvider {
    suspend fun refresh(): ReputationRefreshResult
}

class LocalJsonImportProvider(
    private val streamProvider: () -> InputStream,
    private val manager: ImportExportManager,
) : ReputationSourceProvider {
    override suspend fun refresh(): ReputationRefreshResult =
        manager.importReputation(streamProvider()).toRefreshResult()
}

class HttpsJsonFeedProvider(
    private val feedUrl: String,
    private val manager: ImportExportManager,
) : ReputationSourceProvider {
    override suspend fun refresh(): ReputationRefreshResult {
        val uri = runCatching { URI(feedUrl) }.getOrNull()
            ?: return ReputationRefreshResult.Failed("Ungültige Feed-Adresse.")
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) {
            return ReputationRefreshResult.Failed("Der Feed muss eine gültige HTTPS-Adresse verwenden.")
        }
        return runCatching {
            val connection = URL(feedUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.useCaches = false
            try {
                val code = connection.responseCode
                if (code !in 200..299) return ReputationRefreshResult.Failed("Feed antwortete mit HTTP $code.")
                val contentType = connection.contentType.orEmpty().lowercase()
                if (!contentType.contains("json")) {
                    return ReputationRefreshResult.Failed("Feed liefert keinen JSON-Inhalt.")
                }
                val length = connection.contentLengthLong
                if (length > de.kruemmel.rufwaechter.importexport.ReputationJsonCodec.MAX_FILE_BYTES) {
                    return ReputationRefreshResult.Failed("Feed ist größer als 5 MiB.")
                }
                manager.importReputation(connection.inputStream, "https-feed").toRefreshResult()
            } finally {
                connection.disconnect()
            }
        }.getOrElse { ReputationRefreshResult.Failed("Feed konnte nicht sicher geladen werden.") }
    }
}

class OfflineOnlyProvider : ReputationSourceProvider {
    override suspend fun refresh(): ReputationRefreshResult = ReputationRefreshResult.Offline
}

private fun ReputationParseOutcome.toRefreshResult(): ReputationRefreshResult = when (this) {
    is ReputationParseOutcome.Success -> ReputationRefreshResult.Updated(
        result.accepted.size,
        result.rejected,
        result.sha256,
    )
    is ReputationParseOutcome.Failure -> ReputationRefreshResult.Failed(message)
}
