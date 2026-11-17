package me.avinas.tempo.ui.appearance

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import java.io.IOException
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(
    name = "tempo_appearance",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The appearance store is intentionally separate from listening data and playback settings.
 * Appearance changes cannot migrate Room, reset permissions, or touch tracking services.
 */
@Singleton
class AppearancePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        val VERSION = intPreferencesKey("appearance_schema_version")
        const val CURRENT_VERSION = 1
        val PRESET = stringPreferencesKey("appearance_preset_id")
        val TONE = stringPreferencesKey("appearance_tone")
        val FONT = stringPreferencesKey("appearance_font_style")
        val REDUCED_MOTION = booleanPreferencesKey("appearance_reduce_motion")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.retryWhen { cause, _ ->
        if (cause is IOException) {
            emit(emptyPreferences())
            delay(1_000)
            true
        } else false
    }.map { prefs ->
        AppearanceSettings(
            preset = AppearancePreset.decode(prefs[PRESET]),
            tone = decodeAppearanceTone(prefs[TONE]),
            fontStyle = AppearanceFontStyle.decode(prefs[FONT]),
            reduceMotion = prefs[REDUCED_MOTION] ?: false,
        )
    }

    /** Apply one complete draft in a single transaction. Legacy unversioned data is v0. */
    suspend fun apply(settings: AppearanceSettings) {
        context.appearanceDataStore.edit { prefs ->
            check((prefs[VERSION] ?: 0) <= CURRENT_VERSION) { "Unsupported appearance schema" }
            prefs[VERSION] = CURRENT_VERSION
            prefs[PRESET] = settings.preset.name
            prefs[TONE] = AppearanceTone.DARK.name
            prefs[FONT] = settings.fontStyle.name
            prefs[REDUCED_MOTION] = settings.reduceMotion
        }
    }
}
