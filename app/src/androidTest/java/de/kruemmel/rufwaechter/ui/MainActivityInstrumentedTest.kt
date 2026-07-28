package de.kruemmel.rufwaechter.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import de.kruemmel.rufwaechter.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun DashboardUndNavigationSindSichtbar() {
        composeRule.onNodeWithText("Übersicht").assertIsDisplayed()
        composeRule.onNodeWithText("Prüfen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Regeln")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Daten").assertIsDisplayed()
    }

    @Test fun PhoneBlockEinstellungenSindErreichbar() {
        composeRule.onNodeWithText("Setup").performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(12)
        composeRule.onNodeWithText("PhoneBlock").assertIsDisplayed()
    }

    @Test fun ExportierteSystemSperrlisteKannAusgewaehltWerden() {
        composeRule.onNodeWithText("Daten").performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        composeRule.onNodeWithText("Bisherige System-Sperren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Systemliste öffnen").assertIsDisplayed()
        composeRule.onNodeWithText("Export importieren").assertIsDisplayed()
    }

    @Test fun BebilderteHilfeIstErreichbarUndErklaertDenStart() {
        composeRule.onNodeWithText("Hilfe").performClick()
        composeRule.onNodeWithText("In wenigen Minuten startklar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Smartphone mit Schutzschild und Häkchen").assertIsDisplayed()
        composeRule.onNodeWithText("1. Anrufschutz im System aktivieren").performScrollTo().assertIsDisplayed()
    }
}
