package app.suical.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.suical.MainActivity
import app.suical.data.Repository
import app.suical.domain.Calc
import app.suical.domain.PlanConfig
import app.suical.domain.todayEpochDay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Rappel quotidien : stock bas et veille de session de cuisine. */
object Reminders {
    private const val WORK = "suical-reminders"
    private const val CHANNEL = "suical_reminders"
    private const val LOW_STOCK_DAYS = 3.0

    /** Planifie (ou annule) le rappel quotidien selon la config. À appeler au démarrage et à chaque changement. */
    fun sync(ctx: Context, cfg: PlanConfig) {
        val wm = WorkManager.getInstance(ctx)
        if (!cfg.remindersOn) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(cfg.reminderHour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Les lignes du rappel pour ce jour (vide = rien à signaler). Pur, donc testable. */
    fun messages(c: Calc, today: Long): List<String> {
        val s = c.s
        val cfg = s.config
        val out = ArrayList<String>()

        val low = s.foods.values.filter { c.daysLeft(it.id) < LOW_STOCK_DAYS }
        if (low.isNotEmpty()) {
            out += "Stock bas : " + low.joinToString(", ") {
                val d = c.daysLeft(it.id)
                it.name.lowercase() + if (d < 1) " (moins d'un jour)" else " (~${d.toInt()} j)"
            }
        }

        val dayIdx = Math.floorMod(today - cfg.startEpochDay, cfg.period.toLong()).toInt()
        for (session in cfg.sessions) {
            val first = session.days.filter { it < cfg.days }.minOrNull() ?: continue
            if (dayIdx != Math.floorMod(first - 1, cfg.period)) continue
            val missing = c.sessionPlan(session).missing
            out += "Demain : " + session.whenLabel.lowercase() + ", session de cuisine." +
                if (missing.isEmpty()) " Stock suffisant." else " Il manque : " + missing.joinToString(", ") { m ->
                    s.foods[m.foodId]?.name?.lowercase() ?: m.foodId
                }
        }
        return out
    }

    fun notify(ctx: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Suical")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = Repository(applicationContext).state().first()
        if (!s.config.remindersOn) return Result.success()
        Reminders.notify(applicationContext, Reminders.messages(Calc.of(s), todayEpochDay()))
        return Result.success()
    }
}
