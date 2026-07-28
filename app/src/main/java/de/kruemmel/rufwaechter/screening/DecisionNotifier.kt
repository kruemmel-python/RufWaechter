package de.kruemmel.rufwaechter.screening

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.kruemmel.rufwaechter.MainActivity
import de.kruemmel.rufwaechter.R
import de.kruemmel.rufwaechter.domain.ScreeningAction

class DecisionNotifier(private val context: Context) {
    fun show(action: ScreeningAction, displayNumber: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Anrufentscheidungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Lokale Hinweise zu blockierten oder stummgeschalteten Anrufen"
            },
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (action) {
            ScreeningAction.BLOCK -> "Ein Anruf wurde blockiert."
            ScreeningAction.SILENCE -> "Ein Anruf wurde stummgeschaltet."
            else -> return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RufWächter")
            .setContentText("$text Nummer: ${mask(displayNumber)}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }

    private fun mask(value: String): String =
        if (value.length <= 4) "••••" else "•".repeat(value.length - 4) + value.takeLast(4)

    companion object {
        private const val CHANNEL_ID = "screening_decisions"
    }
}
