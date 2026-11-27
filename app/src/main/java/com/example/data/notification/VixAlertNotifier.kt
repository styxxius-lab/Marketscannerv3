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
import com.example.domain.model.VixSpikeAlertEvent
import java.util.Locale

class VixAlertNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VIX Volatility Spikes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts when the CBOE VIX spikes intraday past your threshold"
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
    fun notifySpike(event: VixSpikeAlertEvent) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TradeExecutionNotifier.EXTRA_TARGET_TAB, TARGET_TAB_SIGNALS)
        }
        val notificationId = "VIX_SPIKE_${event.id}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val vixStr = "%.2f".format(Locale.US, event.vixLevel)
        val baseStr = "%.2f".format(Locale.US, event.baselineLevel)
        val spikeStr = "+%.1f%%".format(Locale.US, event.spikePct)
        val threshStr = "+%.1f%%".format(Locale.US, event.thresholdPct)

        val title = "VIX Volatility Spike: $vixStr ($spikeStr)"
        val summary = "CBOE VIX surged $spikeStr intraday past $threshStr threshold"
        val detail = "CBOE VIX climbed from $baseStr to $vixStr ($spikeStr intraday surge), breaching your $threshStr volatility alert (${event.source}). Consider tightening stops or reducing position size."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_bell)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "vix_volatility_alerts"
        const val TARGET_TAB_SIGNALS = "SIGNALS"
    }
}
