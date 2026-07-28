package de.kruemmel.rufwaechter.phoneblock

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneBlockJsonCodecInstrumentedTest {
    private val codec = PhoneBlockJsonCodec(PhoneNumberParser())

    @Test fun vollabgleichLiestVersionEintraegeUndEntfernungen() {
        val json = """
            {"numbers":[
              {"phone":"+493411234567","rating":"G_FRAUD","votes":10,"lastActivity":1234},
              {"phone":"+493411234568","rating":"E_ADVERTISING","votes":0}
            ],"version":42}
        """.trimIndent()
        val result = codec.parseCommunity(ByteArrayInputStream(json.toByteArray()), 2000)
        assertEquals(42, result.version)
        assertEquals(1, result.entries.size)
        assertEquals(listOf("+493411234568"), result.removals)
        assertEquals("G_FRAUD", result.entries.single().rating)
    }

    @Test fun duplikatWirdDeterministischDurchLetztenWertErsetzt() {
        val json = """
            {"version":43,"numbers":[
              {"phone":"+493411234567","rating":"E_ADVERTISING","votes":2},
              {"phone":"+493411234567","rating":"G_FRAUD","votes":20}
            ]}
        """.trimIndent()
        val result = codec.parseCommunity(ByteArrayInputStream(json.toByteArray()), 2000)
        assertEquals(1, result.entries.size)
        assertEquals(20, result.entries.single().votes)
    }

    @Test fun persoenlicheWhitelistWirdGelesen() {
        val json = """
            {"numbers":[{"phone":"+493411234567","rating":"A_LEGITIMATE","comment":"Arzt","created":1234}]}
        """.trimIndent()
        val result = codec.parsePersonal(
            ByteArrayInputStream(json.toByteArray()),
            PhoneBlockListType.WHITELIST,
            2000,
        )
        assertEquals(1, result.size)
        assertEquals("Arzt", result.single().comment)
        assertEquals(PhoneBlockListType.WHITELIST.name, result.single().listType)
    }

    @Test fun ungueltigeStimmenzahlWirdAbgelehnt() {
        val json = """{"version":1,"numbers":[{"phone":"+493411234567","votes":-1}]}"""
        assertTrue(runCatching {
            codec.parseCommunity(ByteArrayInputStream(json.toByteArray()), 2000)
        }.isFailure)
    }
}
