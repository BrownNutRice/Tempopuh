package me.avinas.tempo.ui.appearance

/** Persisted identifiers; never rename values after shipping without migration. */
enum class AppearancePreset {
    ABYSS, OBSIDIAN_GOLD, AURORA, GLACIER;

    companion object {
        fun decode(raw: String?): AppearancePreset =
            entries.firstOrNull { it.name == raw } ?: ABYSS
    }
}

/**
 * Only dark-mode is currently exposed as a live app-wide mode.
 * Other tone values are reserved for when every existing legacy screen uses
 * semantic text/surface colors, rather than fixed light-on-dark constants.
 */
enum class AppearanceTone { DARK, LIGHT, SYSTEM }

data class AppearanceSettings(
    val preset: AppearancePreset = AppearancePreset.ABYSS,
    val tone: AppearanceTone = AppearanceTone.DARK,
    val reduceMotion: Boolean = false,
)

/** Safe default for older versions or corrupt preference strings. */
internal fun decodeAppearanceTone(raw: String?): AppearanceTone =
    AppearanceTone.entries.firstOrNull { it.name == raw } ?: AppearanceTone.DARK
