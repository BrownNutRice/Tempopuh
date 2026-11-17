package me.avinas.tempo.data.reset

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Full local reset is owned by Android, including process shutdown and app-private stores. */
@Singleton
class AppDataResetter @Inject constructor(@ApplicationContext context: Context) {
    private val request = AppResetRequest {
        context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
    }

    val state = request.state

    // Never fall back to piecemeal Room/preferences deletion while writers are alive.
    // A successful request can terminate this process before this function returns.
    fun requestReset() = request.request()
}
