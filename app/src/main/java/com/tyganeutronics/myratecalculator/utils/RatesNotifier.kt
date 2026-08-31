package com.tyganeutronics.myratecalculator.utils

import android.Manifest
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
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.activities.MainActivity

object RatesNotifier {

    private const val CHANNEL_ID = "rates_refresh"
    private const val COINS_EXHAUSTED_ID = 1001

    /**
     * Tells the user background refreshing has stopped because their coins ran out. When
     * notifications were never granted this quietly does nothing — the schedule still stops,
     * they just find out next time they open the app.
     */
    fun notifyCoinsExhausted(context: Context) {
        createChannel(context)
        if (!canNotify(context)) return

        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = context.getString(R.string.notification_coins_exhausted_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dollar)
            .setContentTitle(context.getString(R.string.notification_coins_exhausted_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(COINS_EXHAUSTED_ID, notification)
    }

    /** Notifications need granting from Android 13 onwards. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_rates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
    }
}
