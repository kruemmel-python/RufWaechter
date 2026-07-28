package de.kruemmel.rufwaechter.phoneblock

import android.util.JsonReader
import de.kruemmel.rufwaechter.data.PhoneBlockEntryEntity
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import java.io.InputStream
import java.io.InputStreamReader

class PhoneBlockJsonCodec(private val phoneNumberParser: PhoneNumberParser) {
    fun parseCommunity(input: InputStream, synchronizedAt: Long): PhoneBlockCommunityPayload {
        var version: Long? = null
        val entries = linkedMapOf<String, PhoneBlockEntryEntity>()
        val removals = linkedSetOf<String>()
        reader(input).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "version" -> version = json.nextLong()
                    "numbers" -> {
                        json.beginArray()
                        while (json.hasNext()) {
                            require(entries.size + removals.size < MAX_ENTRIES) { "PhoneBlock-Liste enthält zu viele Einträge." }
                            readCommunityEntry(json, synchronizedAt)?.let { entry ->
                                if (entry.votes == 0) {
                                    entries.remove(entry.normalizedNumber)
                                    removals += entry.normalizedNumber
                                } else {
                                    removals.remove(entry.normalizedNumber)
                                    entries[entry.normalizedNumber] = entry
                                }
                            }
                        }
                        json.endArray()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }
        return PhoneBlockCommunityPayload(
            version = requireNotNull(version) { "PhoneBlock-Antwort enthält keine Version." }.also {
                require(it >= 0) { "Ungültige PhoneBlock-Version." }
            },
            entries = entries.values.toList(),
            removals = removals.toList(),
        )
    }

    fun parsePersonal(
        input: InputStream,
        listType: PhoneBlockListType,
        synchronizedAt: Long,
    ): List<PhoneBlockEntryEntity> {
        require(listType != PhoneBlockListType.COMMUNITY)
        val entries = linkedMapOf<String, PhoneBlockEntryEntity>()
        reader(input).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() != "numbers") {
                    json.skipValue()
                    continue
                }
                json.beginArray()
                while (json.hasNext()) {
                    require(entries.size < MAX_ENTRIES) { "Persönliche PhoneBlock-Liste enthält zu viele Einträge." }
                    readPersonalEntry(json, listType, synchronizedAt)?.let { entries[it.normalizedNumber] = it }
                }
                json.endArray()
            }
            json.endObject()
        }
        return entries.values.toList()
    }

    private fun readCommunityEntry(reader: JsonReader, synchronizedAt: Long): PhoneBlockEntryEntity? {
        var phone: String? = null
        var rating: String? = null
        var votes = -1
        var lastActivity: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "phone" -> phone = reader.nextString()
                "rating" -> rating = readNullableString(reader)?.takeIf(::validRating)
                "votes" -> votes = reader.nextInt()
                "lastActivity" -> lastActivity = readNullableLong(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val normalized = normalize(phone) ?: return null
        require(votes in 0..1_000_000) { "Ungültige Stimmenzahl in der PhoneBlock-Liste." }
        return PhoneBlockEntryEntity(
            normalizedNumber = normalized,
            listType = PhoneBlockListType.COMMUNITY.name,
            rating = rating,
            votes = votes,
            comment = "",
            lastActivity = lastActivity?.takeIf { it > 0 },
            updatedAt = synchronizedAt,
        )
    }

    private fun readPersonalEntry(
        reader: JsonReader,
        listType: PhoneBlockListType,
        synchronizedAt: Long,
    ): PhoneBlockEntryEntity? {
        var phone: String? = null
        var rating: String? = null
        var comment = ""
        var created: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "phone" -> phone = reader.nextString()
                "rating" -> rating = readNullableString(reader)?.takeIf(::validRating)
                "comment" -> comment = readNullableString(reader).orEmpty().take(500)
                "created" -> created = readNullableLong(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val normalized = normalize(phone) ?: return null
        return PhoneBlockEntryEntity(
            normalizedNumber = normalized,
            listType = listType.name,
            rating = rating,
            votes = 0,
            comment = comment,
            lastActivity = created?.takeIf { it > 0 },
            updatedAt = synchronizedAt,
        )
    }

    private fun readNullableString(reader: JsonReader): String? =
        if (reader.peek() == android.util.JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextString()
        }

    private fun readNullableLong(reader: JsonReader): Long? =
        if (reader.peek() == android.util.JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextLong()
        }

    private fun normalize(raw: String?): String? =
        (raw?.let(phoneNumberParser::parse) as? PhoneIdentity.Number)?.normalized?.value

    private fun validRating(value: String): Boolean = value in VALID_RATINGS

    private fun reader(input: InputStream): JsonReader =
        JsonReader(InputStreamReader(BoundedInputStream(input, MAX_BYTES), Charsets.UTF_8)).apply {
            isLenient = false
        }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long,
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) {
                if (delegate.read() == -1) return -1
                throw IllegalArgumentException("PhoneBlock-Antwort ist zu groß.")
            }
            val value = delegate.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) {
                if (delegate.read() == -1) return -1
                throw IllegalArgumentException("PhoneBlock-Antwort ist zu groß.")
            }
            val count = delegate.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }

        override fun close() = delegate.close()
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        const val MAX_ENTRIES = 250_000
        private val VALID_RATINGS = setOf(
            "A_LEGITIMATE",
            "B_MISSED",
            "C_PING",
            "D_POLL",
            "E_ADVERTISING",
            "F_GAMBLE",
            "G_FRAUD",
        )
    }
}
