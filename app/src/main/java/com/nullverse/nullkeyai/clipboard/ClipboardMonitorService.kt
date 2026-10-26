package com.nullverse.nullkeyai.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.db.NullKeyDatabase
import com.nullverse.nullkeyai.sync.DeviceIdentity
import com.nullverse.nullkeyai.db.ClipCaptureMethod
import com.nullverse.nullkeyai.db.ClipContentType
import com.nullverse.nullkeyai.db.ClipSourceConfidence
import com.nullverse.nullkeyai.ui.MainActivity
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
    private lateinit var assetStore: VaultAssetStore

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClip()
    }

    override fun onCreate() {
        super.onCreate()
        assetStore = VaultAssetStore(this)
        repository = ClipRepository(NullKeyDatabase.get(this).clipDao(), assetStore, deviceIdentity = DeviceIdentity.from(this))
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
                uri != null -> {
                    val mimeType = clip.description?.getMimeType(0)
                    try {
                        val asset = assetStore.importUri(uri, mimeType)
                        val inserted = repository.capture(
                            ClipCaptureRequest(
                                content = uri.toString(),
                                contentType = if (mimeType?.startsWith("image/") == true) ClipContentType.IMAGE else ClipContentType.FILE,
                                isFile = true,
                                mimeType = mimeType,
                                localAssetPath = asset.relativePath,
                                sourceUri = uri.toString(),
                                captureMethod = ClipCaptureMethod.CLIPBOARD,
                                sourceConfidence = ClipSourceConfidence.UNKNOWN
                            )
                        )
                        if (inserted == null) assetStore.delete(asset.relativePath)
                    } catch (_: Exception) {
                        // URI permission may be temporary or unavailable. Keep metadata,
                        // but never pretend the asset was persisted successfully.
                        repository.capture(
                            ClipCaptureRequest(
                                content = uri.toString(),
                                contentType = if (mimeType?.startsWith("image/") == true) ClipContentType.IMAGE else ClipContentType.FILE,
                                isFile = true,
                                mimeType = mimeType,
                                sourceUri = uri.toString(),
                                captureMethod = ClipCaptureMethod.CLIPBOARD,
                                sourceConfidence = ClipSourceConfidence.UNKNOWN
                            )
                        )
                    }
                }
                !text.isNullOrBlank() -> repository.capture(
                    ClipCaptureRequest(
                        content = text,
                        contentType = ClipContentType.TEXT,
                        captureMethod = ClipCaptureMethod.CLIPBOARD,
                        sourceConfidence = ClipSourceConfidence.UNKNOWN
                    )
                )
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
        val vaultIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val vaultPendingIntent = PendingIntent.getActivity(
            this, 0, vaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(getString(R.string.monitor_text))
            .setSmallIcon(R.drawable.ic_clip)
            .setContentIntent(vaultPendingIntent)
            .addAction(R.drawable.ic_clip, getString(R.string.open_vault), vaultPendingIntent)
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

        /** API 34+ requires an explicit specialUse type matching the manifest. */
        fun foregroundServiceType(sdkInt: Int = Build.VERSION.SDK_INT): Int =
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
    }
}
