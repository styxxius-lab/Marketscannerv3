package com.example.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.HighImpactNewsEvent
import kotlin.math.roundToInt

class NewsCatalystNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Watchlist AI News Catalysts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the AI news scorer detects a high-impact catalyst on your watchlist"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyHighImpact(event: HighImpactNewsEvent) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TradeExecutionNotifier.EXTRA_TARGET_TAB, TARGET_TAB_NEWS)
        }
        val notificationId = event.eventKey.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dirWord = if (event.direction >= 0.0) "Bullish" else "Bearish"
        val matPct = (event.materiality * 100.0).roundToInt()
        val title = "${event.symbol} High-Impact $dirWord Catalyst ($matPct%)"
        val shortText = event.title
        val rationalePart = if (event.rationale.isNotBlank() && event.rationale != "demo") {
            "\nAI Scorer: ${event.rationale}"
        } else {
            ""
        }
        val detail = "${event.title}$rationalePart"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_bell)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "watchlist_news_catalysts"
        const val TARGET_TAB_NEWS = "NEWS"
    }
}
