package me.avinas.tempo.ui.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.avinas.tempo.BuildConfig
import me.avinas.tempo.data.analytics.AnalyticsConsent
import me.avinas.tempo.data.diagnostics.DiagnosticsInput
import me.avinas.tempo.data.diagnostics.DiagnosticsReport
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.repository.AppPreferenceRepository
import me.avinas.tempo.data.repository.EnrichedMetadataRepository
import me.avinas.tempo.service.TrackingServiceHeartbeat
import me.avinas.tempo.service.OfflineEventStore
import me.avinas.tempo.ui.onboarding.isBatteryOptimizationDisabled
import me.avinas.tempo.ui.permissions.isNotificationListenerEnabled
import javax.inject.Inject

/**
 * Gathers the facts for a user-initiated diagnostics report.
 *
 * Nothing here is transmitted. The report is rendered on screen and only leaves the device if
 * the user explicitly shares it, which is what allows it to be far more detailed than the
 * anonymous analytics schema.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val enrichedMetadataRepository: EnrichedMetadataRepository,
    private val appPreferenceRepository: AppPreferenceRepository,
    private val analyticsConsent: AnalyticsConsent
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private val offlineStore = OfflineEventStore(context)

    init {
        refresh()
    }

    fun refresh() {
        // Rapid taps should never allow an older report to overwrite the latest.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, report = null)
            val recoveryRequired = withContext(Dispatchers.IO) {
                runCatching { offlineStore.hasRecoveryData() }.getOrNull()
            }
            val report = try {
                DiagnosticsReport.build(collectInputs(recoveryRequired))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false, report = report,
                recoveryRequired = recoveryRequired ?: _uiState.value.recoveryRequired,
            )
        }
    }

    fun discardRecoveryData() {
        if (_uiState.value.discardingRecovery) return
        _uiState.value = _uiState.value.copy(discardingRecovery = true, recoveryDiscardFailed = false)
        viewModelScope.launch {
            val discarded = withContext(Dispatchers.IO) {
                runCatching { offlineStore.discardRecoveryData() }.isSuccess
            }
            _uiState.value = _uiState.value.copy(discardingRecovery = false, recoveryDiscardFailed = !discarded)
            refresh()
        }
    }

    private suspend fun collectInputs(recoveryRequired: Boolean?): DiagnosticsInput = withContext(Dispatchers.IO) {
        val heartbeat = TrackingServiceHeartbeat.snapshot(context)
        val capturedAt = System.currentTimeMillis()
        fun ageSince(timestamp: Long): Long? =
            timestamp.takeIf { it > 0L && it <= capturedAt }?.let { capturedAt - it }
        val allApps = appPreferenceRepository.getAllApps().first()
        val enabledApps = appPreferenceRepository.getEnabledApps().first()

        DiagnosticsInput(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidApi = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL ?: "unknown",
            databaseVersion = AppDatabase.VERSION,
            trackCount = database.trackDao().getCount(),
            artistCount = database.artistDao().getCount(),
            albumCount = database.albumDao().getAlbumCount(),
            eventCount = database.listeningEventDao().getCount(),
            enrichmentCounts = runCatching {
                enrichedMetadataRepository.getEnrichmentStats()
                    .mapKeys { (status, _) -> status.name }
            }.getOrDefault(emptyMap()),
            notificationAccessGranted = isNotificationListenerEnabled(context),
            listenerConnected = heartbeat.listenerConnected,
            listenerAliveAgoMillis = ageSince(heartbeat.lastServiceAliveAt),
            lastNotificationCallbackAgoMillis = ageSince(heartbeat.lastNotificationCallbackAt),
            lastMediaSessionCallbackAgoMillis = ageSince(heartbeat.lastMediaSessionCallbackAt),
            batteryOptimisationExempt = isBatteryOptimizationDisabled(context),
            // Read through AnalyticsConsent so the DataStore key names stay owned in one place.
            analyticsEnabled = analyticsConsent.isEnabled.first(),
            analyticsDisclosureSeen = analyticsConsent.isDisclosureSeen.first(),
            trackedAppCount = allApps.size,
            enabledAppCount = enabledApps.size,
            offlineQueueRecoveryRequired = recoveryRequired,
            workerStates = scheduledWorkerStates()
        )
    }

    /**
     * Reads WorkManager state directly rather than through the worker companions, because most
     * of those hold their unique work names privately.
     */
    private fun scheduledWorkerStates(): Map<String, String> {
        val workManager = WorkManager.getInstance(context)
        return TRACKED_WORKERS.associateWith { name ->
            runCatching {
                val infos = workManager.getWorkInfosForUniqueWork(name).get()
                if (infos.isEmpty()) "none" else infos.joinToString("/") { it.state.label() }
            }.getOrDefault("unknown")
        }
    }

    private fun WorkInfo.State.label(): String = when (this) {
        WorkInfo.State.ENQUEUED -> "enqueued"
        WorkInfo.State.RUNNING -> "running"
        WorkInfo.State.SUCCEEDED -> "succeeded"
        WorkInfo.State.FAILED -> "failed"
        WorkInfo.State.BLOCKED -> "blocked"
        WorkInfo.State.CANCELLED -> "cancelled"
    }

    private companion object {
        val TRACKED_WORKERS = listOf(
            "service_health_check",
            "music_enrichment",
            "local_backup",
            "drive_backup",
            "analytics_flush",
            "gamification_refresh",
            "spotify_enrichment"
        )
    }
}

data class DiagnosticsUiState(
    val isLoading: Boolean = true,
    val report: String? = null,
    val recoveryRequired: Boolean = false,
    val discardingRecovery: Boolean = false,
    val recoveryDiscardFailed: Boolean = false,
)
