package com.appsfolder.livebridge.bridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class LiveBridgeNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "LiveBridgeNLS"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected on SDK ${android.os.Build.VERSION.SDK_INT}")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        if (item.packageName == packageName) return

        val extras = item.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = if (!bigText.isNullOrBlank()) bigText else text

        if (title.isBlank() && body.isBlank()) return

        val maxProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val currentProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val progressPercent: Int? = when {
            indeterminate -> null
            maxProgress > 0 -> ((currentProgress.coerceAtLeast(0).toFloat() / maxProgress.toFloat()) * 100f)
                .toInt()
                .coerceIn(0, 100)
            else -> null
        }

        val otp = BridgePayload.extractOtp("$title $body")
        val category = BridgePayload.detectCategory(item.packageName, title, body)

        if (item.isOngoing && progressPercent == null && !indeterminate && otp == null) {
            return
        }

        val payload = BridgePayload(
            id = bridgeIdFor(item),
            sourcePackage = item.packageName,
            title = title,
            body = body,
            category = category,
            progress = progressPercent,
            indeterminate = indeterminate,
            otpCode = otp,
            ongoing = otp == null
        )

        Oxygen15BridgeService.upsert(this, payload)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        if (item.packageName == packageName) return

        Oxygen15BridgeService.dismiss(this, bridgeIdFor(item))
    }

    private fun bridgeIdFor(sbn: StatusBarNotification): Int {
        val hash = sbn.key.hashCode()
        val positive = if (hash == Int.MIN_VALUE) 1 else kotlin.math.abs(hash)
        return (positive % 800_000_000) + 10_000
    }
}
