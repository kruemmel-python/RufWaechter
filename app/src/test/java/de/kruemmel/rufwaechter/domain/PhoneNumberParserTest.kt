package de.kruemmel.rufwaechter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberParserTest {
    private val parser = PhoneNumberParser()

    @Test fun `deutsche nationale Nummer wird normalisiert`() {
        assertNumber("+493411234567", parser.parse("0341 1234567"))
    }

    @Test fun `internationale Nummer bleibt international`() {
        assertNumber("+493411234567", parser.parse("+49 (341) 123-4567"))
    }

    @Test fun `doppelnull wird zu Plus`() {
        assertNumber("+442071234567", parser.parse("0044 20 7123 4567"))
    }

    @Test fun `tel URI wird akzeptiert`() {
        assertNumber("+493411234567", parser.parse("tel:+49-341-1234567"))
    }

    @Test fun `leere Eingabe ist unbekannt`() {
        assertEquals(PhoneIdentity.UnknownNumber, parser.parse("   "))
        assertEquals(PhoneIdentity.UnknownNumber, parser.parse(null))
    }

    @Test fun `private Kennzeichnung ist privat`() {
        assertEquals(PhoneIdentity.PrivateNumber, parser.parse("anonymous"))
    }

    @Test fun `fremdes Schema wird abgelehnt`() {
        assertEquals(PhoneIdentity.UnsupportedHandle("sip"), parser.parse("alice@example.org", "sip"))
    }

    @Test fun `ueberlange Eingabe ist unbekannt`() {
        assertEquals(PhoneIdentity.UnknownNumber, parser.parse("1".repeat(65)))
    }

    @Test fun `Buchstaben werden nicht als Vanity Nummer geraten`() {
        assertEquals(PhoneIdentity.UnknownNumber, parser.parse("0800-FLOWERS"))
    }

    @Test fun `mehrere Pluszeichen sind unbekannt`() {
        assertEquals(PhoneIdentity.UnknownNumber, parser.parse("+49+3411234567"))
    }

    @Test fun `Normalisierung ist idempotent`() {
        val first = parser.parse("+493411234567") as PhoneIdentity.Number
        assertEquals(first, parser.parse(first.normalized.value))
    }

    private fun assertNumber(expected: String, actual: PhoneIdentity) {
        assertTrue(actual is PhoneIdentity.Number)
        assertEquals(expected, (actual as PhoneIdentity.Number).normalized.value)
    }
}
