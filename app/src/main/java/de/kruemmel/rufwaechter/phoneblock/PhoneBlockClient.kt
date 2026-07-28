package de.kruemmel.rufwaechter.phoneblock

import android.util.Base64
import de.kruemmel.rufwaechter.data.PhoneBlockPendingReportEntity
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject

class PhoneBlockClient(
    private val codec: PhoneBlockJsonCodec,
) {
    fun fetchCommunity(
        credentials: PhoneBlockCredentials,
        since: Long?,
        synchronizedAt: Long,
    ): PhoneBlockCommunityPayload = request(
        path = "blocklist",
        query = since?.let { "since=$it&format=json" } ?: "format=json",
        credentials = credentials,
    ) { connection ->
        validateJsonResponse(connection)
        codec.parseCommunity(connection.inputStream, synchronizedAt)
    }

    fun fetchPersonal(
        credentials: PhoneBlockCredentials,
        type: PhoneBlockListType,
        synchronizedAt: Long,
    ): List<de.kruemmel.rufwaechter.data.PhoneBlockEntryEntity> = request(
        path = when (type) {
            PhoneBlockListType.BLACKLIST -> "blacklist"
            PhoneBlockListType.WHITELIST -> "whitelist"
            PhoneBlockListType.COMMUNITY -> error("Community ist keine persönliche Liste.")
        },
        credentials = credentials,
    ) { connection ->
        validateJsonResponse(connection)
        codec.parsePersonal(connection.inputStream, type, synchronizedAt)
    }

    fun report(credentials: PhoneBlockCredentials, report: PhoneBlockPendingReportEntity) {
        request(
            path = "rate",
            credentials = credentials,
            method = "POST",
            body = JSONObject()
                .put("phone", report.normalizedNumber)
                .put("rating", report.rating)
                .put("comment", report.comment)
                .toString()
                .toByteArray(Charsets.UTF_8),
        ) { Unit }
    }

    private fun <T> request(
        path: String,
        query: String? = null,
        credentials: PhoneBlockCredentials,
        method: String = "GET",
        body: ByteArray? = null,
        transform: (HttpURLConnection) -> T,
    ): T {
        require(credentials.isValid())
        require(path in ALLOWED_PATHS)
        val uri = URI("$API_BASE/$path${query?.let { "?$it" }.orEmpty()}")
        require(uri.scheme == "https" && uri.host == API_HOST)
        val connection = URL(uri.toASCIIString()).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", authorization(credentials))
        if (body != null) {
            require(body.size <= MAX_REQUEST_BYTES)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body) }
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw PhoneBlockHttpException(
                    status = status,
                    retryable = status == 408 || status == 429 || status >= 500,
                )
            }
            return transform(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun validateJsonResponse(connection: HttpURLConnection) {
        val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
        if (type != "application/json") throw PhoneBlockProtocolException("PhoneBlock lieferte keinen JSON-Inhalt.")
        val length = connection.contentLengthLong
        if (length > PhoneBlockJsonCodec.MAX_BYTES) {
            throw PhoneBlockProtocolException("PhoneBlock-Antwort überschreitet 25 MiB.")
        }
    }

    private fun authorization(credentials: PhoneBlockCredentials): String = when (credentials.mode) {
        PhoneBlockAuthMode.BASIC -> {
            val value = "${credentials.username}:${credentials.secret}".toByteArray(Charsets.UTF_8)
            try {
                "Basic ${Base64.encodeToString(value, Base64.NO_WRAP)}"
            } finally {
                value.fill(0)
            }
        }
        PhoneBlockAuthMode.API_KEY -> "Bearer ${credentials.secret}"
    }

    companion object {
        private const val API_HOST = "phoneblock.net"
        private const val API_BASE = "https://phoneblock.net/phoneblock/api"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REQUEST_BYTES = 8 * 1024
        private val ALLOWED_PATHS = setOf("blocklist", "blacklist", "whitelist", "rate")
    }
}

class PhoneBlockHttpException(
    val status: Int,
    val retryable: Boolean,
) : Exception("PhoneBlock antwortete mit HTTP $status.")

class PhoneBlockProtocolException(message: String) : Exception(message)
