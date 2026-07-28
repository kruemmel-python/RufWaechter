package de.kruemmel.rufwaechter.phoneblock

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneBlockCredentialStoreInstrumentedTest {
    private val store by lazy {
        PhoneBlockCredentialStore(ApplicationProvider.getApplicationContext())
    }

    @Before fun resetBefore() = store.clear()
    @After fun resetAfter() = store.clear()

    @Test fun ZugangsdatenWerdenVerschluesseltGespeichertUndGelesen() {
        val expected = PhoneBlockCredentials(PhoneBlockAuthMode.BASIC, "test-user", "test-password")
        store.save(expected)
        assertTrue(store.isConfigured())
        assertEquals(expected, store.load())
    }

    @Test fun LoeschenEntferntDateiUndSchluessel() {
        store.save(PhoneBlockCredentials(PhoneBlockAuthMode.API_KEY, "", "test-api-key"))
        store.clear()
        assertFalse(store.isConfigured())
        assertNull(store.load())
    }
}
