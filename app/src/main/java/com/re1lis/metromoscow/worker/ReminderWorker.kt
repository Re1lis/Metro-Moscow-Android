package com.re1lis.metromoscow.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.re1lis.metromoscow.MainActivity
import com.re1lis.metromoscow.R
import com.re1lis.metromoscow.data.MetroRepository
import com.re1lis.metromoscow.data.MetroSeed
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = MetroRepository(applicationContext)
        val progress = repo.loadProgress()
        val total = MetroSeed.lines.sumOf { it.stations.size }
        val visited = progress.values.count { it.visited }

        // Don't remind if all stations visited
        if (visited >= total) return Result.success()

        showNotification(visited = visited, total = total)
        return Result.success()
    }

    private fun showNotification(visited: Int, total: Int) {
        val ctx = applicationContext
        val channelId = "metro_reminder"
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания Метро Москвы",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Еженедельное напоминание о прогрессе"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remaining = total - visited
        val messages = listOf(
            "Вы посетили $visited из $total станций. Осталось ещё $remaining!",
            "Новая неделя — новые станции! Прогресс: $visited / $total 🚇",
            "Исследуйте метро дальше! Осталось $remaining станций из $total.",
            "$visited станций уже отмечено. Продолжайте путешествие!"
        )
        val message = messages.random()

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Метро Москвы")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    companion object {
        private const val WORK_NAME = "metro_weekly_reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(3, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
