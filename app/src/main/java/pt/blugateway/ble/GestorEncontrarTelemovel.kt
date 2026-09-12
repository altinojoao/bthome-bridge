package pt.blugateway.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pt.blugateway.R

/**
 * Toca um alarme contínuo no telemóvel quando acionado por um clique
 * no beacon -- permite encontrar o telemóvel mesmo em modo silencioso.
 *
 * O alarme:
 *  - Toca no stream RING (máximo volume, ignora modo silencioso com
 *    setMode NORMAL temporariamente)
 *  - Vibra em padrão SOS enquanto toca
 *  - Mostra uma notificação de alta prioridade com botão "Parar"
 *  - Para automaticamente após DURACAO_MAX_MS se não for dispensado
 *
 * Dispensar: tocar no botão "Parar" da notificação, ou a app chamar
 * para() directamente (ex: via botão na UI).
 */
object GestorEncontrarTelemovel {

    private const val CANAL_ID = "encontrar_telemovel"
    private const val NOTIF_ID = 9001
    private const val DURACAO_MAX_MS = 60_000L  // para automaticamente após 1 min
    private const val ACTION_PARAR = "pt.blugateway.PARAR_ALARME_TELEMOVEL"

    @Volatile private var emAlarme = false
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pararAutomatico = Runnable { para(null) }

    fun estaEmAlarme(): Boolean = emAlarme

    /** Chamado pela acção ENCONTRAR_TELEMOVEL no ExecutorAcoes. */
    fun toca(context: Context) {
        if (emAlarme) return  // já está a tocar
        emAlarme = true

        val ctx = context.applicationContext

        // Tocar ringtone em volume máximo, ignorando modo silencioso
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, volMax, 0)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // fallback: usar ToneGenerator se o MediaPlayer falhar
            GestorSons.tocaAlarmeAlcance()
        }

        // Vibração em padrão SOS: ...---...
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            // padrão: [espera, liga, pausa, liga, ...] em ms
            // SOS: 3 curtos, 3 longos, 3 curtos, pausa longa
            val padrao = longArrayOf(
                0,100,100,100,100,100,200,  // ...
                300,100,300,100,300,200,     // ---
                100,100,100,100,100,1000     // ...  + pausa
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(padrao, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(padrao, 0)
            }
        } catch (_: Exception) {}

        // Notificação com botão Parar
        criaCanal(ctx)
        val intentParar = Intent(ACTION_PARAR).also {
            it.setPackage(ctx.packageName)
        }
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
        } catch (_: SecurityException) {}

        // Parar automaticamente após 1 minuto
        handler.postDelayed(pararAutomatico, DURACAO_MAX_MS)
    }

    /** Para o alarme -- chamado pelo BroadcastReceiver do botão ou pela UI. */
    fun para(context: Context?) {
        if (!emAlarme) return
        emAlarme = false
        handler.removeCallbacks(pararAutomatico)

        player?.let {
            try { if (it.isPlaying) it.stop(); it.release() } catch (_: Exception) {}
        }
        player = null

        vibrator?.let {
            try { it.cancel() } catch (_: Exception) {}
        }
        vibrator = null

        context?.applicationContext?.let { ctx ->
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
        }
    }

    private fun criaCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL_ID,
            context.getString(R.string.encontrar_telemovel_titulo),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.encontrar_telemovel_desc)
            setSound(null, null)  // o som vem do MediaPlayer, não da notificação
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(canal)
    }
}
