package com.madar.launcher.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.madar.launcher.MadarApp
import com.madar.launcher.MainActivity
import com.madar.launcher.R
import com.madar.launcher.ui.Haptics

/** Schedules the gentle nudges: intent-gate time budgets running out and focus sessions ending. */
object Reminders {

    private const val CHANNEL = "nudges"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_KIND = "kind"
    const val KIND_GATE = "gate"
    const val KIND_POMODORO = "pomodoro"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, requestCode: Int, atMillis: Long, kind: String, title: String, text: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, requestCode, kind, title, text)
        if (alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(context: Context, requestCode: Int, kind: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, requestCode, kind, "", ""))
    }

    private fun pending(context: Context, requestCode: Int, kind: String, title: String, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.madar.launcher.NUDGE.$kind")
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_madar)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(Reminders.EXTRA_KIND) ?: return
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT).orEmpty()
        Reminders.notify(context, kind.hashCode(), title, text)
        Haptics(context).completion()
        if (kind == Reminders.KIND_POMODORO) {
            (context.applicationContext as MadarApp).settings.edit { it.copy(pomodoroEndsAt = 0) }
        }
    }
}
