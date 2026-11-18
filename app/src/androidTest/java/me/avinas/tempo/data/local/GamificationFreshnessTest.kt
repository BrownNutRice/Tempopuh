package me.avinas.tempo.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GamificationFreshnessTest {
    @Test
    fun persistedStampSurvivesReopenAndRejectsAStaleRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "gamification-test-${UUID.randomUUID()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (table in listOf(
                        "listening_events", "track_artists", "tracks", "enriched_metadata",
                        "daily_challenges", "user_level", "badges",
                    )) {
                        db.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY, value INTEGER)")
                    }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val factory = FrameworkSQLiteOpenHelperFactory()

        try {
            factory.create(config).use { helper ->
                val db = helper.writableDatabase
                GamificationFreshness.install(db)
                val initial = GamificationFreshness.read(db)
                GamificationFreshness.markFresh(db, initial.revision, "current-day")
            }
            factory.create(config).use { helper ->
                val db = helper.writableDatabase
                GamificationFreshness.install(db)
                val previous = GamificationFreshness.read(db)
                assertEquals("current-day", previous.refreshedFor)
                db.execSQL("INSERT INTO listening_events VALUES (1, 10)")
                GamificationFreshness.markFresh(db, previous.revision, "stale-computation")
                assertNull(GamificationFreshness.read(db).refreshedFor)

                val changed = GamificationFreshness.read(db)
                GamificationFreshness.markFresh(db, changed.revision, "current-day")
                db.execSQL("UPDATE listening_events SET value = 20 WHERE id = 1")
                assertNull(GamificationFreshness.read(db).refreshedFor)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
