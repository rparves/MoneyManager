package com.rparves.moneymanager.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rparves.moneymanager.EXTRA_ADD
import com.rparves.moneymanager.MainActivity
import com.rparves.moneymanager.R
import com.rparves.moneymanager.app
import com.rparves.moneymanager.data.Backup
import com.rparves.moneymanager.ui.money
import com.rparves.moneymanager.ui.startMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminder {
    const val CHANNEL = "daily_reminder"
    private const val REQ = 2300
    private const val NOTIF_ID = 2300

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Reminds you to record the day's expenses"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Schedules the next daily tick (reminder and/or auto backup) at the chosen time, 11 PM by default. */
    fun schedule(ctx: Context) {
        val prefs = ctx.app().prefs
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (!prefs.reminderOn && !prefs.autoBackup) return
        val now = ZonedDateTime.now()
        var next = now.withHour(prefs.reminderHour).withMinute(prefs.reminderMinute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.toInstant().toEpochMilli()
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    suspend fun notifyNow(ctx: Context) {
        val app = ctx.app()
        val today = LocalDate.now()
        val from = today.startMillis()
        val to = today.plusDays(1).startMillis()
        val count = app.dao.countBetween(from, to)
        val spent = app.dao.expenseBetween(from, to)
        val cur = app.prefs.currency
        val tomorrow = today.plusDays(1)
        val due = app.dao.billsList().filter { !it.isPaid(today) && (it.nextDue(today) == today || it.nextDue(today) == tomorrow) }

        val title = if (count == 0) "Did you spend anything today?" else "Anything else to record today?"
        var text = if (count == 0) {
            "You haven't recorded any expenses today. Take a minute to add them."
        } else {
            "You've logged $count ${if (count == 1) "entry" else "entries"} today, ${money(spent, cur)} spent."
        }
        if (due.isNotEmpty()) text += " Due soon: " + due.joinToString { it.name } + "."
        show(ctx, title, text)
    }

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_ADD, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF8A3D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Add expense", open)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = ctx.app().prefs
                if (prefs.reminderOn) runCatching { Reminder.notifyNow(ctx) }
                if (prefs.autoBackup) runCatching { Backup.autoBackup(ctx) }
            } finally {
                Reminder.schedule(ctx)
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.schedule(context.applicationContext)
    }
}
