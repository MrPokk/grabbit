package com.grabbit.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import android.os.Build
import android.os.IBinder
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
 * [DownloadEngine]; this service keeps the process alive and shows a single
 * ongoing download notification built by [DownloadNotificationManager].
 */
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject
    lateinit var engine: DownloadEngine

    private lateinit var notifications: DownloadNotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = DownloadNotificationManager(this, NotificationManagerCompat.from(this))
        DownloadNotificationManager.createChannels(this)
        val initial = engine.tasks.value
        ServiceCompat.startForeground(
            this,
            UiValues.FOREGROUND_NOTIFICATION_ID,
            notifications.build(initial),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        scope.launch {
            engine.tasks.collect { tasks ->
                notifications.post(tasks)
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
        notifications.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val ACTION_PAUSE = "com.grabbit.action.PAUSE"
        private const val ACTION_RESUME = "com.grabbit.action.RESUME"
        private const val ACTION_CANCEL = "com.grabbit.action.CANCEL"

        fun start(context: Context) {
            DownloadNotificationManager.start(context)
        }

        fun createChannels(context: Context) {
            DownloadNotificationManager.createChannels(context)
        }
    }
}
