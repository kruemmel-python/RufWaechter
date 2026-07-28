package de.kruemmel.rufwaechter.importexport

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReputationJsonCodecInstrumentedTest {
    private val codec = ReputationJsonCodec(PhoneNumberParser())

    @Test fun gueltigeDateiWirdGelesen() {
        val outcome = codec.parse(ByteArrayInputStream(validJson().toByteArray()))
        assertTrue(outcome is ReputationParseOutcome.Success)
        val result = (outcome as ReputationParseOutcome.Success).result
        assertEquals(1, result.read)
        assertEquals(1, result.accepted.size)
        assertEquals(0, result.rejected)
        assertEquals(64, result.sha256.length)
    }

    @Test fun unbekannteSchemaVersionWirdAbgelehnt() {
        val outcome = codec.parse(ByteArrayInputStream(validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":2").toByteArray()))
        assertTrue(outcome is ReputationParseOutcome.Failure)
    }

    @Test fun ungueltigerScoreWirdAlsVerworfenGemeldet() {
        val outcome = codec.parse(ByteArrayInputStream(validJson().replace("\"spamScore\":90", "\"spamScore\":101").toByteArray()))
        assertTrue(outcome is ReputationParseOutcome.Success)
        assertEquals(1, (outcome as ReputationParseOutcome.Success).result.rejected)
    }

    @Test fun DuplikatWirdDeterministischVerworfen() {
        val entry = entryJson()
        val json = rootJson("$entry,$entry")
        val outcome = codec.parse(ByteArrayInputStream(json.toByteArray()))
        assertTrue(outcome is ReputationParseOutcome.Success)
        assertEquals(1, (outcome as ReputationParseOutcome.Success).result.rejected)
    }

    @Test fun zuGrosseDateiWirdVorDemParsenAbgelehnt() {
        val oversized = ByteArray(ReputationJsonCodec.MAX_FILE_BYTES + 1) { ' '.code.toByte() }
        val outcome = codec.parse(ByteArrayInputStream(oversized))
        assertTrue(outcome is ReputationParseOutcome.Failure)
    }

    private fun validJson() = rootJson(entryJson())

    private fun rootJson(entries: String) =
        """{"schemaVersion":1,"generatedAt":"2026-07-28T18:00:00Z","source":{"name":"Test","version":"1"},"entries":[$entries]}"""

    private fun entryJson() =
        """{"number":"+493411234567","spamScore":90,"confidence":90,"category":"FRAUD","reportCount":10,"positiveCount":0,"sourceCount":3,"lastUpdated":"2026-07-28T12:00:00Z","expiresAt":"2030-08-28T12:00:00Z"}"""
}
