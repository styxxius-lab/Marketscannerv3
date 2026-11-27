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
import com.example.domain.model.BotState
import com.example.domain.model.TradeLogEntry
import com.example.domain.model.TradePosition
import java.util.Locale
import kotlin.math.abs

class TradeExecutionNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Autotrader Executions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Local push notifications when the paper autotrader opens or closes a position"
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
    fun notifyExecution(
        entry: TradeLogEntry,
        openPosition: TradePosition?,
        afterState: BotState
    ) {
        if (!hasNotificationPermission()) return
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, TARGET_TAB_TRADE)
        }
        val notificationId = "${entry.type}_${entry.symbol}_${entry.timestampMs}".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val qtyStr = "%.4f".format(Locale.US, entry.qty).trimEnd('0').trimEnd('.')
        val priceStr = "$" + "%.2f".format(Locale.US, entry.price)

        val title: String
        val summary: String
        val detail: String

        if (entry.type.equals("OPEN", ignoreCase = true)) {
            title = "Autotrader Opened ${entry.symbol}"
            summary = "Bought $qtyStr ${entry.symbol} @ $priceStr"
            val notePart = if (entry.note.isNotBlank()) " (${entry.note})" else ""
            val levelsPart = if (openPosition != null) {
                val stopStr = "$" + "%.2f".format(Locale.US, openPosition.stop)
                val targetStr = "$" + "%.2f".format(Locale.US, openPosition.target)
                " Stop $stopStr · Target $targetStr."
            } else {
                ""
            }
            detail = "Bought $qtyStr ${entry.symbol} @ $priceStr$notePart.$levelsPart"
        } else {
            val pnl = entry.pnl ?: 0.0
            val sign = if (pnl >= 0.0) "+" else "-"
            val pnlStr = "$sign$" + "%.2f".format(Locale.US, abs(pnl))
            val reasonLabel = when (entry.note.lowercase(Locale.US)) {
                "target" -> "Target Reached"
                "stop" -> "Stop-Loss Hit"
                else -> entry.note.ifBlank { "Closed" }.replaceFirstChar { it.uppercase() }
            }
            val eqStr = "$" + "%.2f".format(Locale.US, afterState.equity)
            title = "Autotrader Closed ${entry.symbol} ($pnlStr)"
            summary = "Sold $qtyStr ${entry.symbol} @ $priceStr · $reasonLabel"
            detail = "Closed $qtyStr ${entry.symbol} @ $priceStr ($reasonLabel) · Realized PnL $pnlStr · Equity $eqStr."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trade_signal)
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
        const val CHANNEL_ID = "autotrader_executions"
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val TARGET_TAB_TRADE = "TRADE"
    }
}
