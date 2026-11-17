package me.avinas.tempo.ui.appearance

/** Pure visibility rule. A last-seen playback snapshot must not pin a mini-player forever. */
internal fun shouldShowCompactStrip(
    mode: AppearanceNowListening,
    title: String,
    updatedAtMs: Long,
    nowMs: Long,
): Boolean =
    mode == AppearanceNowListening.COMPACT &&
        title.isNotBlank() &&
        updatedAtMs > 0L &&
        nowMs >= updatedAtMs &&
        nowMs - updatedAtMs < 15 * 60_000L
