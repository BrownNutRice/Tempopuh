package me.avinas.tempo.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import me.avinas.tempo.R
import me.avinas.tempo.widget.utils.WidgetHub
import me.avinas.tempo.widget.utils.cancelPeriodicWidgetUpdateIfUnused
import me.avinas.tempo.widget.utils.scheduleImmediateWidgetUpdate
import me.avinas.tempo.widget.utils.schedulePeriodicWidgetUpdate

class TempoDiscoveryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val songs = prefs[stringPreferencesKey(WidgetHub.PREF_DISCOVERY_NEW_TRACKS)]?.toIntOrNull()
            val artists = prefs[stringPreferencesKey(WidgetHub.PREF_DISCOVERY_NEW_ARTISTS)]?.toIntOrNull()

            ListeningWidgetCard(context, context.getString(R.string.widget_weekly_discovery)) {
                WidgetMetric(
                    songs?.let { context.getString(R.string.stats_period_number, it) } ?: "—",
                    context.getString(R.string.widget_new_songs),
                    GlanceModifier.defaultWeight(),
                    highlighted = true,
                )
                WidgetMetric(
                    artists?.let { context.getString(R.string.stats_period_number, it) } ?: "—",
                    context.getString(R.string.widget_new_artists),
                    GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

class TempoDiscoveryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TempoDiscoveryWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleImmediateWidgetUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicWidgetUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Shared periodic work: this only stops it once the last Tempo widget is gone,
        // so removing one widget does not silently kill refresh for the others.
        cancelPeriodicWidgetUpdateIfUnused(context)
    }
}
