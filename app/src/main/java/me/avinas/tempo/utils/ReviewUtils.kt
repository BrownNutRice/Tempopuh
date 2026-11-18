package me.avinas.tempo.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import me.avinas.tempo.R
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

object ReviewUtils {

    suspend fun launchInAppReview(activity: Activity): Boolean {
        return try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}
