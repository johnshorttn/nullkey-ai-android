package com.nullverse.nullkeyai.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.db.NullKeyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent, low-footprint clipboard monitor. It runs as a foreground service so
 * the system keeps it alive, and listens for primary-clip changes. On Android 10+
 * background clipboard reads are restricted by the platform; capture is most
 * reliable while NullKey is the active IME (see [com.nullverse.nullkeyai.ime.NullKeyImeService]),
 * and this service captures whenever the platform allows it.
 */
class ClipboardMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ClipRepository
    private lateinit var clipboard: ClipboardManager

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClip()
    }

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao())
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun captureCurrentClip() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString()
        scope.launch {
            when {
                uri != null -> repository.capture(
                    content = uri.toString(),
                    isFile = true,
                    mimeType = clip.description?.getMimeType(0)
                )
                !text.isNullOrBlank() -> repository.capture(content = text)
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(getString(R.string.monitor_text))
            .setSmallIcon(R.drawable.ic_clip)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(listener)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "nullkey_clipboard_monitor"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }
}
