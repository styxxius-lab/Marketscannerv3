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
import com.example.domain.engine.SignalChangeTriggerEvent
import com.example.domain.model.AlertCondition
import com.example.domain.model.PriceAlert
import java.util.Locale

class PriceAlertNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val priceChannel = NotificationChannel(
                CHANNEL_ID,
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when watched equities cross your target price thresholds"
                enableVibration(true)
            }
            val signalChannel = NotificationChannel(
                SIGNAL_CHANNEL_ID,
                "AI Signal Change Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push alerts when a watched ticker's AI or quantitative signal flips (BUY / SELL / HOLD)"
                enableVibration(true)
            }
            manager?.createNotificationChannel(priceChannel)
            manager?.createNotificationChannel(signalChannel)
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
    fun notifyTriggered(alert: PriceAlert) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.symbol.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dirText = if (alert.condition == AlertCondition.ABOVE) "crossed above" else "dropped below"
        val targetStr = "$" + "%.2f".format(Locale.US, alert.targetPrice)
        val liveStr = "$" + "%.2f".format(Locale.US, alert.triggeredPrice ?: alert.targetPrice)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_bell)
            .setContentTitle("${alert.symbol} Price Alert Triggered")
            .setContentText("${alert.symbol} $dirText $targetStr (now $liveStr)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${alert.symbol} $dirText your $targetStr threshold — last price $liveStr.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    fun notifySignalChange(event: SignalChangeTriggerEvent) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("sig_" + event.symbol).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priceStr = "$" + "%.2f".format(Locale.US, event.price)
        val convStr = "%.2f".format(Locale.US, event.conviction)
        val transition = "${event.previousAction.name} → ${event.newAction.name}"
        val summaryLine = "${event.symbol} flipped $transition @ $priceStr (Conv $convStr)"
        val detailLine = "$summaryLine · ${event.reason}"

        val notification = NotificationCompat.Builder(context, SIGNAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_bell)
            .setContentTitle("${event.symbol} AI Signal Flip: $transition")
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailLine))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(("sig_" + event.alertId).hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "price_alerts"
        const val SIGNAL_CHANNEL_ID = "ai_signal_alerts"
    }
}
