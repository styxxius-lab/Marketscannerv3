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
import com.example.domain.engine.BackgroundMarketAlert
import com.example.domain.engine.BackgroundMonitorAlertKind

/**
 * Dispatches local Android notifications from [com.example.data.worker.CatalystMonitorWorker]
 * when high-impact catalyst scores or significant market price movements are detected.
 */
class CatalystMarketAlertNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Catalyst & Market Movement Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Background alerts for high-impact AI catalyst scores and significant price moves"
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
    fun notifyAlerts(alerts: List<BackgroundMarketAlert>): Int {
        if (alerts.isEmpty() || !hasNotificationPermission()) return 0
        ensureChannel()

        val manager = NotificationManagerCompat.from(context)
        val capped = alerts.take(MAX_NOTIFICATIONS_PER_SWEEP)
        capped.forEach { alert ->
            val targetTab = if (alert.kind == BackgroundMonitorAlertKind.SIGNIFICANT_MARKET_MOVE) {
                VixAlertNotifier.TARGET_TAB_SIGNALS
            } else {
                NewsCatalystNotifier.TARGET_TAB_NEWS
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(TradeExecutionNotifier.EXTRA_TARGET_TAB, targetTab)
            }
            val notificationId = 71_000 + (alert.dedupeKey.hashCode() and 0x7FFF)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_bell)
                .setContentTitle(alert.title)
                .setContentText(alert.body.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(notificationId, notification)
        }

        if (capped.size > 1) {
            val summaryIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(TradeExecutionNotifier.EXTRA_TARGET_TAB, NewsCatalystNotifier.TARGET_TAB_NEWS)
            }
            val summaryPi = PendingIntent.getActivity(
                context,
                SUMMARY_NOTIFICATION_ID,
                summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val symbolsText = capped.joinToString(", ") { it.symbol }
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert_bell)
                .setContentTitle("${capped.size} Market Catalyst Alerts")
                .setContentText("Significant moves or catalysts on $symbolsText")
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(summaryPi)
                .build()
            manager.notify(SUMMARY_NOTIFICATION_ID, summary)
        }

        return capped.size
    }

    companion object {
        const val CHANNEL_ID = "background_catalyst_market_alerts"
        private const val GROUP_KEY = "com.example.CATALYST_MARKET_ALERTS"
        private const val SUMMARY_NOTIFICATION_ID = 71_999
        private const val MAX_NOTIFICATIONS_PER_SWEEP = 4
    }
}
