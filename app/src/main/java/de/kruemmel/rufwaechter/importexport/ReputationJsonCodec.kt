package de.kruemmel.rufwaechter.importexport

import android.util.JsonReader
import de.kruemmel.rufwaechter.domain.NormalizedPhoneNumber
import de.kruemmel.rufwaechter.domain.NumberReputation
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import de.kruemmel.rufwaechter.domain.ReputationCategory
import de.kruemmel.rufwaechter.domain.ReputationSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

data class ReputationImportResult(
    val read: Int,
    val accepted: List<NumberReputation>,
    val rejected: Int,
    val warnings: List<String>,
    val sha256: String,
    val sourceName: String,
    val sourceVersion: String,
)

sealed interface ReputationParseOutcome {
    data class Success(val result: ReputationImportResult) : ReputationParseOutcome
    data class Failure(val message: String, val sha256: String?) : ReputationParseOutcome
}

class ReputationJsonCodec(
    private val phoneNumberParser: PhoneNumberParser,
) {
    fun parse(input: InputStream, nowEpochMs: Long = System.currentTimeMillis()): ReputationParseOutcome {
        val bytes = try {
            input.use { it.readLimited(MAX_FILE_BYTES) }
        } catch (_: SizeLimitExceededException) {
            return ReputationParseOutcome.Failure("Datei ist größer als 5 MiB.", null)
        }
        val sha = bytes.sha256()
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = try {
            JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder))
        } catch (_: Exception) {
            return ReputationParseOutcome.Failure("Datei ist nicht gültig UTF-8-kodiert.", sha)
        }
        return runCatching {
            parseRoot(reader, nowEpochMs, sha)
        }.fold(
            onSuccess = { ReputationParseOutcome.Success(it) },
            onFailure = { ReputationParseOutcome.Failure(it.message ?: "Ungültiges JSON.", sha) },
        )
    }

    private fun parseRoot(reader: JsonReader, now: Long, sha: String): ReputationImportResult {
        var schemaVersion: Int? = null
        var sourceName = "Unbekannte Quelle"
        var sourceVersion = ""
        val accepted = mutableListOf<NumberReputation>()
        val warnings = mutableListOf<String>()
        val seenNumbers = mutableSetOf<String>()
        var read = 0
        var rejected = 0
        val seenFields = mutableSetOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(seenFields.add(name)) { "Doppeltes Hauptfeld: $name" }
            when (name) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "generatedAt" -> Instant.parse(reader.nextString())
                "source" -> {
                    val source = readSource(reader)
                    sourceName = source.first
                    sourceVersion = source.second
                }
                "entries" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(read < MAX_RECORDS) { "Mehr als $MAX_RECORDS Datensätze." }
                        read++
                        val parsed = runCatching { readEntry(reader, sourceName, sourceVersion, now) }
                        parsed.fold(
                            onSuccess = { entry ->
                                if (seenNumbers.add(entry.number.value)) {
                                    accepted += entry
                                } else {
                                    rejected++
                                    warnings += "Datensatz $read: doppelte Nummer verworfen."
                                }
                            },
                            onFailure = {
                                rejected++
                                warnings += "Datensatz $read: ${it.message ?: "ungültig"}"
                            },
                        )
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(schemaVersion == 1) { "Nicht unterstützte schemaVersion: ${schemaVersion ?: "fehlt"}." }
        require(read > 0) { "Der Feed enthält keine Datensätze." }
        val finalEntries = accepted.map {
            it.copy(provenance = listOf(ReputationSource(sourceName, sourceVersion)))
        }
        return ReputationImportResult(
            read,
            finalEntries,
            rejected,
            warnings.take(MAX_WARNINGS),
            sha,
            sourceName.take(MAX_SOURCE_LENGTH),
            sourceVersion.take(MAX_VERSION_LENGTH),
        )
    }

    private fun readSource(reader: JsonReader): Pair<String, String> {
        var name = "Unbekannte Quelle"
        var version = ""
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(seen.add(key)) { "Doppeltes Quellfeld: $key" }
            when (key) {
                "name" -> name = reader.nextString().take(MAX_SOURCE_LENGTH)
                "version" -> version = reader.nextString().take(MAX_VERSION_LENGTH)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return name to version
    }

    private fun readEntry(
        reader: JsonReader,
        sourceName: String,
        sourceVersion: String,
        now: Long,
    ): NumberReputation {
        var number: String? = null
        var score: Int? = null
        var confidence: Int? = null
        var category: String? = null
        var reportCount = 0
        var positiveCount = 0
        var sourceCount = 1
        var lastUpdated: Long? = null
        var expiresAt: Long? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(seen.add(key)) { "doppeltes Feld $key" }
            when (key) {
                "number" -> number = reader.nextString()
                "spamScore" -> score = reader.nextInt()
                "confidence" -> confidence = reader.nextInt()
                "category" -> category = reader.nextString()
                "reportCount" -> reportCount = reader.nextInt()
                "positiveCount" -> positiveCount = reader.nextInt()
                "sourceCount" -> sourceCount = reader.nextInt()
                "lastUpdated" -> lastUpdated = Instant.parse(reader.nextString()).toEpochMilli()
                "expiresAt" -> expiresAt = Instant.parse(reader.nextString()).toEpochMilli()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val identity = phoneNumberParser.parse(number)
        val normalized = (identity as? PhoneIdentity.Number)?.normalized
            ?: throw IllegalArgumentException("ungültige Rufnummer")
        require(score in 0..100) { "spamScore außerhalb 0–100" }
        require(confidence in 0..100) { "confidence außerhalb 0–100" }
        require(reportCount >= 0 && positiveCount >= 0 && sourceCount >= 0) { "negativer Zähler" }
        require(expiresAt == null || expiresAt > now) { "Datensatz ist abgelaufen" }
        val parsedCategory = runCatching { ReputationCategory.valueOf(category.orEmpty()) }
            .getOrElse { throw IllegalArgumentException("unzulässige Kategorie") }
        return NumberReputation(
            number = NormalizedPhoneNumber.fromCanonical(normalized.value)
                ?: throw IllegalArgumentException("ungültige Normalform"),
            spamScore = score ?: throw IllegalArgumentException("spamScore fehlt"),
            confidence = confidence ?: throw IllegalArgumentException("confidence fehlt"),
            category = parsedCategory,
            reportCount = reportCount,
            positiveCount = positiveCount,
            sourceCount = sourceCount,
            lastUpdatedEpochMs = lastUpdated ?: throw IllegalArgumentException("lastUpdated fehlt"),
            expiresAtEpochMs = expiresAt,
            provenance = listOf(ReputationSource(sourceName, sourceVersion)),
        )
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw SizeLimitExceededException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private class SizeLimitExceededException : Exception()

    companion object {
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
        const val MAX_RECORDS = 50_000
        private const val MAX_WARNINGS = 100
        private const val MAX_SOURCE_LENGTH = 100
        private const val MAX_VERSION_LENGTH = 50
    }
}
