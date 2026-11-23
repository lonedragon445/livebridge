package com.appsfolder.livebridge.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class Oxygen15BridgeService : Service() {

    companion object {
        const val CHANNEL_ONGOING_ID = "livebridge_o15_ongoing"
        const val CHANNEL_OTP_ID = "livebridge_o15_otp"
        const val ACTION_UPSERT = "com.appsfolder.livebridge.ACTION_UPSERT"
        const val ACTION_DISMISS = "com.appsfolder.livebridge.ACTION_DISMISS"
        const val ACTION_COPY_OTP = "com.appsfolder.livebridge.ACTION_COPY_OTP"

        const val EXTRA_ID = "extra_id"
        const val EXTRA_PKG = "extra_pkg"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_INDETERMINATE = "extra_indeterminate"
        const val EXTRA_OTP = "extra_otp"
        const val EXTRA_CATEGORY = "extra_category"

        fun upsert(context: Context, payload: BridgePayload) {
            val intent = Intent(context, Oxygen15BridgeService::class.java).apply {
                action = ACTION_UPSERT
                putExtra(EXTRA_ID, payload.id)
                putExtra(EXTRA_PKG, payload.sourcePackage)
                putExtra(EXTRA_TITLE, payload.title)
                putExtra(EXTRA_BODY, payload.body)
                putExtra(EXTRA_PROGRESS, payload.progress ?: -1)
                putExtra(EXTRA_INDETERMINATE, payload.indeterminate)
                putExtra(EXTRA_OTP, payload.otpCode)
                putExtra(EXTRA_CATEGORY, payload.category)
            }
            context.startForegroundService(intent)
        }

        fun dismiss(context: Context, notificationId: Int) {
            val intent = Intent(context, Oxygen15BridgeService::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ID, notificationId)
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private val activeIds = linkedSetOf<Int>()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        startForeground(9001, buildBaseForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPSERT -> {
                val id = intent.getIntExtra(EXTRA_ID, 9002)
                val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
                val rawProgress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
                val otp = intent.getStringExtra(EXTRA_OTP) ?: BridgePayload.extractOtp("$title $body")
                val category = intent.getStringExtra(EXTRA_CATEGORY)
                    ?: BridgePayload.detectCategory(pkg, title, body)

                val payload = BridgePayload(
                    id = id,
                    sourcePackage = pkg,
                    title = title,
                    body = body,
                    category = category,
                    progress = if (rawProgress >= 0) rawProgress.coerceIn(0, 100) else null,
                    indeterminate = indeterminate,
                    otpCode = otp,
                    ongoing = otp == null
                )

                if (Build.VERSION.SDK_INT >= 36) {
                    postAndroid15CompatibleNotification(payload)
                } else {
                    postAndroid15CompatibleNotification(payload)
                }
            }

            ACTION_DISMISS -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                if (id != -1) {
                    notificationManager.cancel(id)
                    activeIds.remove(id)
                }
                if (activeIds.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }

            ACTION_COPY_OTP -> {
                val otp = intent.getStringExtra(EXTRA_OTP)
                val id = intent.getIntExtra(EXTRA_ID, -1)
                if (!otp.isNullOrBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OTP", otp))
                }
                if (id != -1) {
                    notificationManager.cancel(id)
                    activeIds.remove(id)
                }
            }
        }
        return START_STICKY
    }

    private fun postAndroid15CompatibleNotification(payload: BridgePayload) {
        val isOtp = !payload.otpCode.isNullOrBlank()
        val channelId = if (isOtp) CHANNEL_OTP_ID else CHANNEL_ONGOING_ID

        val formattedTitle = if (isOtp) {
            "OTP ${payload.otpCode} · ${payload.title.ifBlank { payload.sourcePackage }}"
        } else {
            payload.title.ifBlank { payload.sourcePackage }
        }

        val statusPrefix = "[${payload.category.uppercase()}] "
        val formattedText = if (payload.body.startsWith("[")) payload.body else "$statusPrefix${payload.body}"

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(formattedTitle)
            .setContentText(formattedText)
            .setSubText(payload.sourcePackage)
            .setOnlyAlertOnce(true)
            .setOngoing(!isOtp && payload.ongoing)
            .setAutoCancel(isOtp)
            .setPriority(if (isOtp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        if (payload.indeterminate) {
            builder.setProgress(0, 0, true)
        } else if (payload.progress != null) {
            builder.setProgress(payload.maxProgress, payload.progress, false)
        }

        if (isOtp) {
            val copyIntent = Intent(this, Oxygen15BridgeService::class.java).apply {
                action = ACTION_COPY_OTP
                putExtra(EXTRA_ID, payload.id)
                putExtra(EXTRA_OTP, payload.otpCode)
            }
            val copyPending = PendingIntent.getService(
                this,
                payload.id * 10 + 1,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Copy OTP", copyPending)
        }

        val dismissIntent = Intent(this, Oxygen15BridgeService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_ID, payload.id)
        }
        val dismissPending = PendingIntent.getService(
            this,
            payload.id * 10 + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Dismiss", dismissPending)

        activeIds.add(payload.id)
        notificationManager.notify(payload.id, builder.build())
    }

    private fun buildBaseForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("LiveBridge Active")
            .setContentText("Monitoring notifications for OxygenOS 15 bridge")
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannels() {
        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING_ID,
            "LiveBridge Ongoing Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent progress and status updates on Android 15"
        }

        val otpChannel = NotificationChannel(
            CHANNEL_OTP_ID,
            "LiveBridge OTP Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Quick-copy OTP alerts extracted from incoming notifications"
        }

        notificationManager.createNotificationChannel(ongoingChannel)
        notificationManager.createNotificationChannel(otpChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
