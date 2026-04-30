package com.midas.voip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.midas.MainActivity
import com.midas.R

/**
 * ForegroundService que mantiene vivo el proceso mientras hay una llamada
 * Chime activa. Equivalente al rol que CallKit cumple en iOS (mantener el
 * proceso despierto + UI sistema).
 *
 * Tipo `microphone` (no `phoneCall` — ese requiere ser default-dialer y Play
 * Console rechaza apps de terceros). El sistema permite captura continua
 * mientras la app está backgrounded sin matar el proceso.
 *
 * Lifecycle:
 *   AndroidVoipDispatcher.dial()      -> start(context, displayName)
 *   AndroidVoipDispatcher.hangup()    -> stop(context)
 *   AndroidChimeMeetingController.onAudioSessionStopped(error)
 *                                     -> stop(context)
 */
class MidasCallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val displayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME) ?: "llamada"
        ensureChannel()

        val tapIntent = Intent(this, MainActivity::class.java)
        tapIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val tapPending = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Llamada en curso")
            .setContentText("Hablando con $displayName")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(tapPending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Llamadas en curso",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notificación persistente mientras hay una llamada VoIP activa."
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "midas_voip_calls"
        private const val NOTIF_ID = 7421
        private const val EXTRA_DISPLAY_NAME = "displayName"

        fun start(context: Context, displayName: String?) {
            val intent = Intent(context, MidasCallForegroundService::class.java).apply {
                putExtra(EXTRA_DISPLAY_NAME, displayName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MidasCallForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
