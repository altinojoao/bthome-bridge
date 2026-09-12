package pt.blugateway.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pt.blugateway.R

/**
 * Toca um alarme no telemóvel quando acionado por um clique no beacon.
 * Funciona em modo silencioso (usa STREAM_ALARM).
 * Para via notificação (botão Parar) ou automaticamente após 60s.
 */
object GestorEncontrarTelemovel {

    private const val CANAL_ID = "encontrar_telemovel"
    private const val NOTIF_ID = 9001
    private const val ACTION_PARAR = "pt.blugateway.PARAR_ALARME_TELEMOVEL"
    private const val DURACAO_MAX_MS = 60_000L

    @Volatile private var emAlarme = false
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pararAutomatico = Runnable { para(null) }

    fun estaEmAlarme(): Boolean = emAlarme

    fun toca(context: Context) {
        if (emAlarme) return
        emAlarme = true

        val ctx = context.applicationContext

        // Aumentar volume de alarme ao máximo
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (_: Exception) {}

        // Tocar ringtone de alarme em loop
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(ctx, uri)?.also { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt.isLooping = true
                }
                rt.audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                rt.play()
            }
        } catch (_: Exception) {
            // fallback: usar ToneGenerator
            GestorSons.tocaAlarmeAlcance()
        }

        // Vibração simples em loop (API 26+)
        try {
            @Suppress("DEPRECATION")
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            val padrao = longArrayOf(0, 200, 100, 200, 100, 600, 200, 200, 100, 200, 100, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(android.os.VibrationEffect.createWaveform(padrao, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(padrao, 0)
            }
        } catch (_: Exception) {}

        // Notificação com botão Parar
        criaCanal(ctx)
        val intentParar = Intent(ACTION_PARAR).setPackage(ctx.packageName)
        val piParar = PendingIntent.getBroadcast(
            ctx, 0, intentParar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(ctx.getString(R.string.encontrar_telemovel_titulo))
            .setContentText(ctx.getString(R.string.encontrar_telemovel_desc))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,
                ctx.getString(R.string.encontrar_telemovel_parar), piParar)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {}

        handler.postDelayed(pararAutomatico, DURACAO_MAX_MS)
    }

    fun para(context: Context?) {
        if (!emAlarme) return
        emAlarme = false
        handler.removeCallbacks(pararAutomatico)
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try {
            @Suppress("DEPRECATION")
            val vib = context?.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vib?.cancel()
        } catch (_: Exception) {}
        context?.applicationContext?.let {
            NotificationManagerCompat.from(it).cancel(NOTIF_ID)
        }
    }

    private fun criaCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CANAL_ID) != null) return
        val canal = NotificationChannel(
            CANAL_ID,
            context.getString(R.string.encontrar_telemovel_titulo),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(canal)
    }
}
