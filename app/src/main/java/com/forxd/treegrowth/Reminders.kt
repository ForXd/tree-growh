package com.forxd.treegrowth

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminders {
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context: Context, prefs: Preferences) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context))
        if (!prefs.reminders) return
        var next = ZonedDateTime.now().withHour(prefs.hour).withMinute(prefs.minute).withSecond(0).withNano(0)
        if (next <= ZonedDateTime.now()) next = next.plusDays(1)
        // A gentle daily reminder tolerates system batching; no exact-alarm permission.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(context))
    }
    fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val data = GardenStore(context.applicationContext as Application).read()
                Reminders.schedule(context, data.preferences)
                val remaining = data.due(LocalDate.now()).size - data.completed(LocalDate.now())
                if (data.preferences.reminders && remaining > 0 && Reminders.allowed(context)) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.createNotificationChannel(NotificationChannel("daily", "每日温柔提醒", NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    nm.notify(1, NotificationCompat.Builder(context, "daily").setSmallIcon(com.forxd.treegrowth.R.drawable.ic_seed)
                        .setContentTitle("给自己一点生长的时间").setContentText("今天还有 $remaining 个小习惯。完成一点，也很好。")
                        .setContentIntent(open).setAutoCancel(true).build())
                }
            } catch (_: Exception) { /* A failed read must not overwrite or notify with invented data. */ } finally { pending.finish() }
        }.start()
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        Thread { try { Reminders.schedule(context, GardenStore(context.applicationContext as Application).read().preferences) } catch (_: Exception) { /* Keep existing data untouched. */ } finally { pending.finish() } }.start()
    }
}
