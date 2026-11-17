package me.avinas.tempo.ui.appearance

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import me.avinas.tempo.ui.theme.AppFontFamily

/**
 * Cached per selected enum by TempoTheme. Only headings change from the shipped
 * Tempo typography; all four choices preserve normal body readability and
 * Android system font scaling.
 */
object AppearanceTypography {
    fun resolve(style: AppearanceFontStyle): Typography {
        val base = me.avinas.tempo.ui.theme.Typography
        val headingFont = when (style) {
            AppearanceFontStyle.TEMPO -> return base
            AppearanceFontStyle.NEO_SANS -> AppFontFamily
            AppearanceFontStyle.ARCHIVE_SERIF -> FontFamily.Serif
            AppearanceFontStyle.STUDIO_MONO -> FontFamily.Monospace
        }
        return base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = headingFont),
            displayMedium = base.displayMedium.copy(fontFamily = headingFont),
            displaySmall = base.displaySmall.copy(fontFamily = headingFont),
            headlineLarge = base.headlineLarge.copy(fontFamily = headingFont),
            headlineMedium = base.headlineMedium.copy(fontFamily = headingFont),
            headlineSmall = base.headlineSmall.copy(fontFamily = headingFont),
            titleLarge = base.titleLarge.copy(fontFamily = headingFont),
        )
    }
}
