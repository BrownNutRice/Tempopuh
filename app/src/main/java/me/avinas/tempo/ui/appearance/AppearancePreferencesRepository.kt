package me.avinas.tempo.ui.appearance

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "tempo_appearance")

/**
 * The appearance store is intentionally separate from listening data and playback settings.
 * Appearance changes cannot migrate Room, reset permissions, or touch tracking services.
 */
@Singleton
class AppearancePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        val PRESET = stringPreferencesKey("appearance_preset_id")
        val TONE = stringPreferencesKey("appearance_tone")
        val REDUCED_MOTION = booleanPreferencesKey("appearance_reduce_motion")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            preset = AppearancePreset.decode(prefs[PRESET]),
            tone = decodeAppearanceTone(prefs[TONE]),
            reduceMotion = prefs[REDUCED_MOTION] ?: false,
        )
    }

    suspend fun setPreset(preset: AppearancePreset) {
        context.appearanceDataStore.edit { it[PRESET] = preset.name }
    }

    suspend fun setReducedMotion(reduced: Boolean) {
        context.appearanceDataStore.edit { it[REDUCED_MOTION] = reduced }
    }

    /** Keep the legacy dark UI as the safe live option until light-mode migration. */
    suspend fun restoreDefaults() {
        context.appearanceDataStore.edit { prefs ->
            prefs[PRESET] = AppearancePreset.ABYSS.name
            prefs[TONE] = AppearanceTone.DARK.name
            prefs[REDUCED_MOTION] = false
        }
    }
}
