package pt.blugateway.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pt.blugateway.R

object GestorEncontrarTelemovel {

    private const val TAG = "BluGateway"
    private const val CANAL_ID = "encontrar_telemovel"
    private const val NOTIF_ID = 9001
    private const val ACTION_PARAR = "pt.blugateway.PARAR_ALARME_TELEMOVEL"
    private const val DURACAO_MAX_MS = 60_000L

    @Volatile private var emAlarme = false
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: Any? = null  // AudioFocusRequest em API 26+
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pararAutomatico = Runnable { para(null) }

    fun estaEmAlarme(): Boolean = emAlarme

    fun toca(context: Context) {
        // Postar para main thread -- MediaPlayer, AudioManager e Vibrator
        // falham silenciosamente em background threads (Dispatchers.IO)
        mainHandler.post { tocaInterno(context.applicationContext) }
    }

    private fun tocaInterno(ctx: Context) {
        if (emAlarme) {
            Log.d(TAG, "[encontrar] já em alarme, ignorar")
            return
        }
        emAlarme = true
        Log.d(TAG, "[encontrar] toca() início")

        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Pedir audio focus para ALARM -- Android roteia para altifalante
        // mesmo em modo silencioso quando o usage é ALARM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            val result = am.requestAudioFocus(req)
            audioFocusRequest = req
            Log.d(TAG, "[encontrar] audioFocus result=$result (1=GRANTED)")
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        // Volume de alarme ao máximo
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        Log.d(TAG, "[encontrar] volume alarme=$maxVol")

        // MediaPlayer com wake lock -- garante que toca mesmo com ecrã apagado
        try {
            val uri: Uri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_ALARM
            ) ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

            Log.d(TAG, "[encontrar] URI alarme=$uri")

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(ctx, uri)
                isLooping = true
                prepare()  // síncrono -- URI local, não tem problema
                start()
            }
            mediaPlayer = mp
            Log.d(TAG, "[encontrar] MediaPlayer a tocar: ${mp.isPlaying}")
        } catch (e: Exception) {
            Log.e(TAG, "[encontrar] MediaPlayer falhou: ${e.message}")
            // Fallback: ToneGenerator no stream ALARM
            try {
                val tg = android.media.ToneGenerator(AudioManager.STREAM_ALARM, 100)
                tg.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, DURACAO_MAX_MS.toInt())
                Log.d(TAG, "[encontrar] ToneGenerator fallback iniciado")
            } catch (e2: Exception) {
                Log.e(TAG, "[encontrar] ToneGenerator também falhou: ${e2.message}")
            }
        }

        // Vibração
        try {
            @Suppress("DEPRECATION")
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            val padrao = longArrayOf(0, 300, 200, 300, 200, 800, 300, 300, 200, 300, 200, 1200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(android.os.VibrationEffect.createWaveform(padrao, 0))
                Log.d(TAG, "[encontrar] vibração VibrationEffect iniciada")
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(padrao, 0)
                Log.d(TAG, "[encontrar] vibração legacy iniciada")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[encontrar] vibração falhou: ${e.message}")
        }

        // Notificação
        mostrarNotificacao(ctx)

        // Parar automaticamente
        mainHandler.postDelayed(pararAutomatico, DURACAO_MAX_MS)
        Log.d(TAG, "[encontrar] toca() completo")
    }

    fun para(context: Context?) {
        if (!emAlarme) return
        emAlarme = false
        mainHandler.removeCallbacks(pararAutomatico)
        Log.d(TAG, "[encontrar] para()")

        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null

        // Largar audio focus
        context?.applicationContext?.let { ctx ->
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        }

        try {
            @Suppress("DEPRECATION")
            val vib = context?.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vib?.cancel()
        } catch (e: Exception) {}

        context?.applicationContext?.let {
            NotificationManagerCompat.from(it).cancel(NOTIF_ID)
        }
    }

    private fun mostrarNotificacao(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CANAL_ID) == null) {
                val canal = NotificationChannel(
                    CANAL_ID,
                    ctx.getString(R.string.encontrar_telemovel_titulo),
                    NotificationManager.IMPORTANCE_HIGH
                )
                canal.setSound(null, null)
                canal.enableVibration(false)
                nm.createNotificationChannel(canal)
            }
        }
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
            .addAction(
                android.R.drawable.ic_media_pause,
                ctx.getString(R.string.encontrar_telemovel_parar),
                piParar
            )
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {
            Log.e(TAG, "[encontrar] sem permissão de notificação: ${e.message}")
        }
    }
}
