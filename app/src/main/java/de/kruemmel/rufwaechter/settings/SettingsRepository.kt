package de.kruemmel.rufwaechter.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningSettings
import de.kruemmel.rufwaechter.domain.ScreeningThresholds
import de.kruemmel.rufwaechter.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.screeningDataStore by preferencesDataStore(name = "screening_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<ScreeningSettings> = context.screeningDataStore.data.map { prefs ->
        val warn = prefs[Keys.WARN_AT] ?: 35
        val silence = prefs[Keys.SILENCE_AT] ?: 60
        val block = prefs[Keys.BLOCK_AT] ?: 80
        val thresholds = ScreeningThresholds(warn, silence, block)
            .takeIf { it.isValid } ?: ScreeningThresholds()
        ScreeningSettings(
            protectionEnabled = prefs[Keys.PROTECTION] ?: true,
            defaultAction = prefs.enum(Keys.DEFAULT_ACTION, ScreeningAction.ALLOW),
            privateNumberAction = prefs.enum(Keys.PRIVATE_ACTION, ScreeningAction.ALLOW),
            unknownNumberAction = prefs.enum(Keys.UNKNOWN_ACTION, ScreeningAction.ALLOW),
            thresholds = thresholds,
            minimumBlockConfidence = (prefs[Keys.MIN_CONFIDENCE] ?: 75).coerceIn(0, 100),
            historyRetentionDays = (prefs[Keys.RETENTION_DAYS] ?: 30).coerceIn(1, 365),
            onlineUpdatesEnabled = prefs[Keys.ONLINE_UPDATES] ?: false,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: true,
            updateIntervalHours = (prefs[Keys.UPDATE_INTERVAL] ?: 24).coerceIn(6, 168),
            feedUrl = prefs[Keys.FEED_URL].orEmpty(),
            phoneBlockEnabled = prefs[Keys.PHONEBLOCK_ENABLED] ?: false,
            phoneBlockContribute = prefs[Keys.PHONEBLOCK_CONTRIBUTE] ?: false,
            phoneBlockDefaultRating = prefs[Keys.PHONEBLOCK_RATING]
                ?.takeIf { it in PHONEBLOCK_RATINGS } ?: "E_ADVERTISING",
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: false,
            themeMode = prefs.enum(Keys.THEME_MODE, ThemeMode.SYSTEM),
        )
    }

    suspend fun update(value: ScreeningSettings) {
        context.screeningDataStore.edit { prefs ->
            prefs[Keys.PROTECTION] = value.protectionEnabled
            prefs[Keys.DEFAULT_ACTION] = value.defaultAction.name
            prefs[Keys.PRIVATE_ACTION] = value.privateNumberAction.name
            prefs[Keys.UNKNOWN_ACTION] = value.unknownNumberAction.name
            prefs[Keys.WARN_AT] = value.thresholds.warnAt
            prefs[Keys.SILENCE_AT] = value.thresholds.silenceAt
            prefs[Keys.BLOCK_AT] = value.thresholds.blockAt
            prefs[Keys.MIN_CONFIDENCE] = value.minimumBlockConfidence
            prefs[Keys.RETENTION_DAYS] = value.historyRetentionDays
            prefs[Keys.ONLINE_UPDATES] = value.onlineUpdatesEnabled
            prefs[Keys.WIFI_ONLY] = value.wifiOnly
            prefs[Keys.UPDATE_INTERVAL] = value.updateIntervalHours
            prefs[Keys.FEED_URL] = value.feedUrl
            prefs[Keys.PHONEBLOCK_ENABLED] = value.phoneBlockEnabled
            prefs[Keys.PHONEBLOCK_CONTRIBUTE] = value.phoneBlockContribute
            prefs[Keys.PHONEBLOCK_RATING] = value.phoneBlockDefaultRating
            prefs[Keys.NOTIFICATIONS] = value.notificationsEnabled
            prefs[Keys.THEME_MODE] = value.themeMode.name
        }
    }

    suspend fun clear() = context.screeningDataStore.edit { it.clear() }

    private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enum(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        fallback: T,
    ): T = get(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private object Keys {
        val PROTECTION = booleanPreferencesKey("protection")
        val DEFAULT_ACTION = stringPreferencesKey("default_action")
        val PRIVATE_ACTION = stringPreferencesKey("private_action")
        val UNKNOWN_ACTION = stringPreferencesKey("unknown_action")
        val WARN_AT = intPreferencesKey("warn_at")
        val SILENCE_AT = intPreferencesKey("silence_at")
        val BLOCK_AT = intPreferencesKey("block_at")
        val MIN_CONFIDENCE = intPreferencesKey("min_confidence")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val ONLINE_UPDATES = booleanPreferencesKey("online_updates")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val UPDATE_INTERVAL = intPreferencesKey("update_interval")
        val FEED_URL = stringPreferencesKey("feed_url")
        val PHONEBLOCK_ENABLED = booleanPreferencesKey("phoneblock_enabled")
        val PHONEBLOCK_CONTRIBUTE = booleanPreferencesKey("phoneblock_contribute")
        val PHONEBLOCK_RATING = stringPreferencesKey("phoneblock_rating")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    companion object {
        private val PHONEBLOCK_RATINGS = setOf(
            "B_MISSED",
            "C_PING",
            "D_POLL",
            "E_ADVERTISING",
            "F_GAMBLE",
            "G_FRAUD",
        )
    }
}
