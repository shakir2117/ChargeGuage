package com.chargegauge.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chargegauge.ui.theme.knownThemeId
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile("chargegauge_settings") },
    )

    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        dataStore.edit { prefs -> prefs.write(next) }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        monitorWhileCharging = this[MONITOR] ?: true,
        sampleIntervalSec = (this[INTERVAL] ?: 2).coerceIn(1, 10),
        heatAlertEnabled = this[HEAT_ON] ?: true,
        heatAlertC = this[HEAT_C] ?: 45.0,
        slowAlertEnabled = this[SLOW_ON] ?: true,
        slowAlertWatts = this[SLOW_W] ?: 5.0,
        slowAlertLevelBelow = this[SLOW_LEVEL] ?: 90,
        slowAlertMinutes = this[SLOW_MINUTES] ?: 3,
        healthAlertEnabled = this[HEALTH_ON] ?: true,
        healthAlertPercent = this[HEALTH_PCT] ?: 80.0,
        powerScaleEnabled = this[SCALE_ON] ?: false,
        powerScale = allowedPowerScale(this[SCALE] ?: 1.0),
        themeId = knownThemeId(this[THEME] ?: "material_you"),
        amoled = this[AMOLED] ?: false,
        slowSinceMillis = this[SLOW_SINCE]?.takeIf { it >= 0L },
        lastHeatMillis = this[LAST_HEAT] ?: 0L,
        lastSlowMillis = this[LAST_SLOW] ?: 0L,
        lastHealthMillis = this[LAST_HEALTH] ?: 0L,
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        this[MONITOR] = settings.monitorWhileCharging
        this[INTERVAL] = settings.sampleIntervalSec.coerceIn(1, 10)
        this[HEAT_ON] = settings.heatAlertEnabled
        this[HEAT_C] = settings.heatAlertC
        this[SLOW_ON] = settings.slowAlertEnabled
        this[SLOW_W] = settings.slowAlertWatts
        this[SLOW_LEVEL] = settings.slowAlertLevelBelow
        this[SLOW_MINUTES] = settings.slowAlertMinutes
        this[HEALTH_ON] = settings.healthAlertEnabled
        this[HEALTH_PCT] = settings.healthAlertPercent
        this[SCALE_ON] = settings.powerScaleEnabled
        this[SCALE] = allowedPowerScale(settings.powerScale)
        this[THEME] = knownThemeId(settings.themeId)
        this[AMOLED] = settings.amoled
        this[SLOW_SINCE] = settings.slowSinceMillis ?: -1L
        this[LAST_HEAT] = settings.lastHeatMillis
        this[LAST_SLOW] = settings.lastSlowMillis
        this[LAST_HEALTH] = settings.lastHealthMillis
    }

    private companion object {
        val MONITOR = booleanPreferencesKey("monitor")
        val INTERVAL = intPreferencesKey("interval_sec")
        val HEAT_ON = booleanPreferencesKey("heat_on")
        val HEAT_C = doublePreferencesKey("heat_c")
        val SLOW_ON = booleanPreferencesKey("slow_on")
        val SLOW_W = doublePreferencesKey("slow_w")
        val SLOW_LEVEL = intPreferencesKey("slow_level")
        val SLOW_MINUTES = intPreferencesKey("slow_minutes")
        val HEALTH_ON = booleanPreferencesKey("health_on")
        val HEALTH_PCT = doublePreferencesKey("health_pct")
        val SLOW_SINCE = longPreferencesKey("slow_since")
        val LAST_HEAT = longPreferencesKey("last_heat")
        val LAST_SLOW = longPreferencesKey("last_slow")
        val LAST_HEALTH = longPreferencesKey("last_health")
        val SCALE_ON = booleanPreferencesKey("power_scale_on")
        val SCALE = doublePreferencesKey("power_scale")
        val THEME = stringPreferencesKey("theme_id")
        val AMOLED = booleanPreferencesKey("amoled")
    }
}
