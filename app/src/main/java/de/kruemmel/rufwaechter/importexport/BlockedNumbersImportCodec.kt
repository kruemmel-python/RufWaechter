package de.kruemmel.rufwaechter.importexport

import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed interface BlockedNumbersImportResult {
    data class Success(val numbers: List<String>, val rejectedLines: Int) : BlockedNumbersImportResult
    data class Failure(val message: String) : BlockedNumbersImportResult
}

class BlockedNumbersImportCodec(private val parser: PhoneNumberParser) {
    fun parse(input: InputStream): BlockedNumbersImportResult = runCatching {
        val bytes = readBounded(input)
        val text = bytes.toString(Charsets.UTF_8)
        bytes.fill(0)
        val numbers = linkedSetOf<String>()
        var rejected = 0
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val normalized = candidates(line).firstNotNullOfOrNull { candidate ->
                (parser.parse(candidate) as? PhoneIdentity.Number)?.normalized?.value
            }
            if (normalized == null) {
                rejected++
            } else {
                require(numbers.size < MAX_NUMBERS) { "Die Sperrliste enthält mehr als $MAX_NUMBERS Nummern." }
                numbers += normalized
            }
        }
        if (numbers.isEmpty()) {
            BlockedNumbersImportResult.Failure(
                "Keine gültige Rufnummer gefunden. Unterstützt werden Text- und CSV-Dateien mit einer Nummer pro Zeile.",
            )
        } else {
            BlockedNumbersImportResult.Success(numbers.toList(), rejected)
        }
    }.getOrElse {
        BlockedNumbersImportResult.Failure(it.message?.take(200) ?: "Sperrliste konnte nicht gelesen werden.")
    }

    private fun candidates(line: String): Sequence<String> = sequence {
        val trimmed = line.trim()
        yield(trimmed.trim('"', '\'', ',', '[', ']', '{', '}'))
        PHONE_FIELD.findAll(line).forEach { match -> yield(match.groupValues[1]) }
        line.split(',', ';', '\t').forEach { field ->
            yield(field.trim().trim('"', '\''))
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        input.use { source ->
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "Die Sperrlistendatei ist größer als 1 MiB." }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_NUMBERS = 10_000
        private val PHONE_FIELD = Regex(
            """(?i)["']?(?:phone|number|nummer)["']?\s*[:=]\s*["']?([+0-9][+0-9 ()./-]{5,30})""",
        )
    }
}
