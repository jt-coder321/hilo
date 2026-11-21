package com.hilotrainer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hilotrainer.ui.Prefs
import java.util.Calendar

/** Daily practice reminder: an inexact daily alarm that posts a notification if today's goal isn't met. */
object Reminders {
    private const val CHANNEL = "practice"
    private const val REQ = 4201

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Next time the clock reads [hour]:00, today or tomorrow. */
    fun nextTrigger(nowMs: Long, hour: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun apply(context: Context) {
        val s = Prefs(context).loadSettings()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
        setBootReceiver(context, s.reminderOn)
        if (!s.reminderOn) return
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextTrigger(System.currentTimeMillis(), s.reminderHour), AlarmManager.INTERVAL_DAY, pending(context))
    }

    private fun setBootReceiver(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun notify(context: Context) {
        val prefs = Prefs(context)
        val d = prefs.daily()
        if (d.metToday) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Practice reminder", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val left = (d.goal - d.points).coerceAtLeast(0)
        val text = if (d.streak > 0) "$left points to go today to keep your ${d.streak}-day streak." else "$left practice points to reach today's goal."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to practice counting")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(REQ, n)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.notify(context)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.apply(context)
    }
}
