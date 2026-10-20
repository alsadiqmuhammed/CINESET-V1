package com.madar.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.provider.Telephony
import com.madar.launcher.MadarApp
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.DigestItem

/**
 * Captures non-urgent notifications into the digest capsule and dismisses them from the shade,
 * so red badges and buzzes only arrive at the times the user chose.
 */
class DigestListenerService : NotificationListenerService() {

    private val app get() = application as MadarApp

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = app.settings.value
        val hidden = sbn.packageName in settings.hiddenPackages
        // Ghost-space apps never surface in the shade; their messages wait inside the vault.
        if (!hidden && (!settings.digestEnabled || !shouldCollect(sbn))) return
        if (hidden && (sbn.isOngoing || !sbn.isClearable)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val label = app.apps.findPackage(sbn.packageName)?.label ?: sbn.packageName
        app.digest.add(
            DigestItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = label,
                title = title,
                text = text,
                postedAt = sbn.postTime,
                vault = hidden,
            ).apply { contentIntent = sbn.notification.contentIntent },
        )
        cancelNotification(sbn.key)
    }

    private fun shouldCollect(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val pkg = sbn.packageName
        val settings = app.settings.value
        if (pkg == packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return false
        if (sbn.isOngoing || !sbn.isClearable) return false
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category in URGENT_CATEGORIES) return false
        if (n.fullScreenIntent != null) return false
        if (pkg in settings.instantPackages) return false
        if (pkg in settings.digestPackages) return true
        if (pkg == defaultDialer() || pkg == defaultSms()) return false
        // People reach you instantly by default; everything else waits for the capsule.
        val kind = app.apps.findPackage(pkg)?.kind
        return kind != AppKind.COMMS
    }

    private fun defaultDialer(): String? = getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    private fun defaultSms(): String? = Telephony.Sms.getDefaultSmsPackage(this)

    private companion object {
        val URGENT_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_REMINDER,
            Notification.CATEGORY_EVENT,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_ERROR,
            Notification.CATEGORY_PROGRESS,
            "missed_call", // Notification.CATEGORY_MISSED_CALL (API 33)
        )
    }
}
