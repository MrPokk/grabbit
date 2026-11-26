package com.grabbit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grabbit.MainActivity
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Formatters
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadTask

private const val TAG = "DownloadNotificationManager"

/**
 * Builds and updates a single ongoing notification for the entire download process,
 * regardless of how many files are being downloaded simultaneously.
 */
class DownloadNotificationManager(
    private val context: Context,
    private val manager: NotificationManagerCompat,
) {
    private var lastNotifyTime = 0L
    private var lastActiveCount = -1

    /** Posts or cancels the single download notification based on the current tasks. */
    fun post(tasks: List<DownloadTask>) {
        val active = tasks.filter { it.status.isActive }
        if (active.isEmpty()) {
            cancel()
            return
        }
        val now = System.currentTimeMillis()
        val countChanged = active.size != lastActiveCount
        val throttled = now - lastNotifyTime >= Timing.PROGRESS_THROTTLE.inWholeMilliseconds
        if (!countChanged && !throttled) return
        lastNotifyTime = now
        lastActiveCount = active.size
        try {
            manager.notify(UiValues.FOREGROUND_NOTIFICATION_ID, build(active))
        } catch (e: SecurityException) {
            AppLog.e(TAG, "could not post the download notification", e)
        }
    }

    /** Removes the ongoing download notification. */
    fun cancel() {
        try {
            manager.cancel(UiValues.FOREGROUND_NOTIFICATION_ID)
        } catch (e: SecurityException) {
            AppLog.e(TAG, "could not cancel the download notification", e)
        }
        lastActiveCount = -1
    }

    /** Builds (without posting) the notification for [tasks]. Used for the foreground start. */
    fun build(tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.status.isActive }
        return buildNotification(buildState(active))
    }

    private fun buildState(active: List<DownloadTask>): NotificationState {
        val transferring = active.filter { it.progress > 0f }
        val indeterminate = transferring.isEmpty()
        val progress =
            if (indeterminate) {
                0f
            } else {
                transferring.sumOf { it.progress.coerceIn(0f, 1f).toDouble() }.toFloat() / transferring.size
            }
        val currentFile = transferring.maxByOrNull { it.progress }?.fileName
        return NotificationState(
            activeCount = active.size,
            transferringCount = transferring.size,
            progress = progress,
            indeterminate = indeterminate,
            currentFile = currentFile,
        )
    }

    private fun buildNotification(state: NotificationState): Notification {
        val percent = Formatters.percent(state.progress)
        val resources = context.resources
        val contentText =
            resources.getQuantityString(R.plurals.notif_downloading_files, state.activeCount, state.activeCount)
        return NotificationCompat
            .Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSubText(buildSubText(resources, state))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(UiValues.PROGRESS_MAX, percent, state.indeterminate)
            .build()
    }

    private fun buildSubText(
        resources: Resources,
        state: NotificationState,
    ): String {
        val progressText =
            resources.getString(R.string.notif_overall_progress, Formatters.percent(state.progress))
        return when {
            state.indeterminate -> resources.getString(R.string.notif_text_preparing)
            state.transferringCount == 1 && state.currentFile != null ->
                "${state.currentFile} · $progressText"
            else -> {
                val currentFileProgress =
                    resources.getString(
                        R.string.notif_current_file_progress,
                        state.transferringCount,
                        state.activeCount,
                    )
                "$currentFileProgress · $progressText"
            }
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class NotificationState(
        val activeCount: Int,
        val transferringCount: Int,
        val progress: Float,
        val indeterminate: Boolean,
        val currentFile: String?,
    )

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_UPDATES = "updates"

        fun createChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val downloads =
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setDescription(context.getString(R.string.notif_channel_downloads_desc))
                    setShowBadge(false)
                }
            val updates =
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setDescription(context.getString(R.string.notif_channel_updates_desc))
                }
            manager.createNotificationChannels(listOf(downloads, updates))
        }

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "could not start the download service in the background", e)
            }
        }
    }
}
