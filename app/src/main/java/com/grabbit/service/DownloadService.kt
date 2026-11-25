package com.grabbit.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.grabbit.MainActivity
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Formatters
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadEngine
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.download.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DownloadService"

/**
 * Foreground anchor for the download queue. The actual work happens in
 * [DownloadEngine]; this service keeps the process alive, shows progress and
 * exposes pause / cancel actions in the notification shade.
 */
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject
    lateinit var engine: DownloadEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val postedTaskIds = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        ServiceCompat.startForeground(
            this,
            UiValues.FOREGROUND_NOTIFICATION_ID,
            buildForeground(engine.tasks.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        scope.launch {
            engine.tasks.collect { tasks ->
                updateNotifications(tasks)
                if (tasks.none { it.status.isActive }) {
                    delay(Timing.SERVICE_STOP_GRACE.inWholeMilliseconds)
                    if (engine.tasks.value.none { it.status.isActive }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val id = intent?.getStringExtra(EXTRA_TASK_ID)
        when (intent?.action) {
            ACTION_PAUSE -> id?.let { engine.pause(it) }
            ACTION_RESUME -> id?.let { engine.resume(it) }
            ACTION_CANCEL -> id?.let { engine.cancel(it) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // A revoked notification permission must not crash the service, so every
    // post goes through postNotification().
    @SuppressLint("MissingPermission")
    private fun updateNotifications(tasks: List<DownloadTask>) {
        val manager = NotificationManagerCompat.from(this)
        if (!canPostNotifications()) {
            // Still refresh the foreground notification; the system handles the
            // missing permission case on Android 13+.
            postNotification(manager, UiValues.FOREGROUND_NOTIFICATION_ID, buildForeground(tasks))
            return
        }

        val visible =
            tasks.filter {
                it.status.isActive ||
                    it.status == TaskStatus.COMPLETED ||
                    it.status == TaskStatus.FAILED ||
                    it.status == TaskStatus.PAUSED
            }

        visible.forEach { task ->
            val id = notificationId(task.id)
            postedTaskIds.add(id)
            postNotification(manager, id, buildTaskNotification(task))
        }

        val stillVisible = visible.map { notificationId(it.id) }.toSet()
        (postedTaskIds - stillVisible).forEach { stale ->
            manager.cancel(stale)
            postedTaskIds.remove(stale)
        }

        postNotification(manager, UiValues.FOREGROUND_NOTIFICATION_ID, buildForeground(tasks))
    }

    private fun postNotification(
        manager: NotificationManagerCompat,
        id: Int,
        notification: Notification,
    ) {
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            AppLog.e(TAG, "notification permission was revoked", e)
        }
    }

    private fun buildForeground(tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.status.isActive }
        val first = active.firstOrNull()
        val text =
            when {
                active.isEmpty() -> getString(R.string.notif_summary_idle)
                active.size == 1 && first != null -> statusText(first)
                else -> resources.getQuantityString(R.plurals.notif_summary_text, active.size, active.size)
            }
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setOngoing(active.isNotEmpty())
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (first != null) {
            builder.setProgress(
                UiValues.PROGRESS_MAX,
                Formatters.percent(first.progress),
                first.status == TaskStatus.PREPARING,
            )
        }
        return builder.build()
    }

    private fun buildTaskNotification(task: DownloadTask): Notification {
        val title =
            when (task.status) {
                TaskStatus.COMPLETED -> getString(R.string.notif_title_complete)
                TaskStatus.FAILED -> getString(R.string.notif_text_failed)
                else -> getString(R.string.notif_title_downloading, task.title)
            }
        val text =
            when (task.status) {
                TaskStatus.COMPLETED -> task.fileName ?: getString(R.string.notif_title_complete)
                TaskStatus.FAILED ->
                    task.error?.let { getString(it.messageRes) }
                        ?: getString(R.string.notif_text_failed)

                TaskStatus.PAUSED -> getString(R.string.status_paused)
                else -> statusText(task)
            }

        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setGroup(GROUP_KEY)
                .setOnlyAlertOnce(true)
                .setAutoCancel(task.status == TaskStatus.COMPLETED)

        if (task.status.isActive) {
            builder.setProgress(
                UiValues.PROGRESS_MAX,
                Formatters.percent(task.progress),
                task.status == TaskStatus.PREPARING,
            )
            builder.setOngoing(true)
            builder.setCategory(NotificationCompat.CATEGORY_PROGRESS)
            if (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.CONVERTING) {
                builder.addAction(0, getString(R.string.action_pause), serviceAction(ACTION_PAUSE, task.id))
            } else if (task.status == TaskStatus.PAUSED) {
                builder.addAction(0, getString(R.string.action_resume), serviceAction(ACTION_RESUME, task.id))
            }
            builder.addAction(0, getString(R.string.action_cancel), serviceAction(ACTION_CANCEL, task.id))
        } else if (task.status == TaskStatus.PAUSED) {
            builder.addAction(0, getString(R.string.action_resume), serviceAction(ACTION_RESUME, task.id))
            builder.addAction(0, getString(R.string.action_cancel), serviceAction(ACTION_CANCEL, task.id))
        }

        return builder.build()
    }

    private fun statusText(task: DownloadTask): String {
        val percent = Formatters.percent(task.progress)
        return when (task.status) {
            TaskStatus.PREPARING -> getString(R.string.notif_text_preparing)
            TaskStatus.CONVERTING -> getString(R.string.status_converting)
            TaskStatus.SAVING -> getString(R.string.status_saving)
            TaskStatus.QUEUED -> getString(R.string.status_queued)
            else -> {
                val speed = task.speed
                if (speed != null) {
                    getString(R.string.notif_text_progress, percent, speed)
                } else {
                    getString(R.string.notif_text_progress, percent, getString(R.string.status_downloading))
                }
            }
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceAction(
        action: String,
        taskId: String,
    ): PendingIntent {
        val intent =
            Intent(this, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, taskId)
            }
        return PendingIntent.getService(
            this,
            (action + taskId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notificationId(taskId: String): Int = taskId.hashCode()

    companion object {
        private const val CHANNEL_DOWNLOADS = "downloads"
        private const val GROUP_KEY = "com.grabbit.downloads"
        private const val EXTRA_TASK_ID = "task_id"
        private const val ACTION_PAUSE = "com.grabbit.action.PAUSE"
        private const val ACTION_RESUME = "com.grabbit.action.RESUME"
        private const val ACTION_CANCEL = "com.grabbit.action.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "could not start the download service in the background", e)
            }
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val downloads =
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notif_channel_downloads_desc)
                    setShowBadge(false)
                }
            val updates =
                NotificationChannel(
                    CHANNEL_UPDATES,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notif_channel_updates_desc)
                }
            manager.createNotificationChannels(listOf(downloads, updates))
        }

        const val CHANNEL_UPDATES = "updates"
    }
}
