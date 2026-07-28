package de.kruemmel.rufwaechter.importexport

import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedNumbersImportCodecTest {
    private val codec = BlockedNumbersImportCodec(PhoneNumberParser())

    @Test fun `Text und CSV werden normalisiert und dedupliziert`() {
        val input = """
            +49 341 1234567
            "+493411234567";bereits vorhanden
            030 123456
        """.trimIndent()
        val result = codec.parse(ByteArrayInputStream(input.toByteArray()))
        assertTrue(result is BlockedNumbersImportResult.Success)
        assertEquals(
            listOf("+493411234567", "+4930123456"),
            (result as BlockedNumbersImportResult.Success).numbers,
        )
    }

    @Test fun `JSON Phone Feld wird erkannt`() {
        val input = """{"phone":"+493411112222","comment":"alt"}"""
        val result = codec.parse(ByteArrayInputStream(input.toByteArray()))
        assertEquals(listOf("+493411112222"), (result as BlockedNumbersImportResult.Success).numbers)
    }

    @Test fun `Datei ohne Nummer wird abgelehnt`() {
        val result = codec.parse(ByteArrayInputStream("keine Nummer".toByteArray()))
        assertTrue(result is BlockedNumbersImportResult.Failure)
    }

    @Test fun `zu grosse Datei wird abgelehnt`() {
        val result = codec.parse(ByteArrayInputStream(ByteArray(BlockedNumbersImportCodec.MAX_BYTES + 1)))
        assertTrue(result is BlockedNumbersImportResult.Failure)
    }
}
