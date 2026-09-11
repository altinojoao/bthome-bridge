package pt.blugateway.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Gere a localizacao GPS com modo adaptativo baseado em RSSI:
 *
 * - EM MOVIMENTO (RSSI varia >= 3 dBm em 60s): GPS a 1s
 * - PARADO RECENTE (< 5min parado): GPS a 30s (mantém aquecido)
 * - PARADO LONGO (>= 5min): GPS desligado -- religar ao primeiro
 *   sinal de movimento (RSSI)
 *
 * O RSSI do beacon (BLE, sempre activo) funciona como detector de
 * movimento gratuito: quando o utilizador se move, a distância ao
 * beacon muda e o RSSI varia. Quando está parado, o RSSI é estável.
 * Poupança típica: ~95% do consumo GPS face ao modo contínuo a 1s.
 */
object GestorLocalizacao {

    private const val TIMEOUT_MS = 30_000L
    private const val MAX_IDADE_CACHE_MS = 8_000L

    private const val LIMIAR_RSSI_MOVIMENTO = 3      // dBm variação mínima = movimento
    private const val JANELA_MOVIMENTO_MS = 60_000L  // janela de análise RSSI
    private const val TEMPO_GPS_REDUZIDO_MS = 5 * 60_000L  // 5min parado → GPS desliga

    private const val INTERVALO_MOVIMENTO_MS = 1_000L
    private const val INTERVALO_PARADO_MS = 30_000L
    private const val DISTANCIA_M = 0f

    @Volatile private var ultimaLocalizacao: Location? = null
    @Volatile private var modoContinuoActivo = false
    @Volatile private var emMovimento = false
    @Volatile private var ultimoMovimentoEm = 0L
    private var listenerContinuo: LocationListener? = null
    private val historicoRssi = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<Pair<Long, Int>>>()

    fun temPermissao(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun temPermissaoSegundoPlano(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return temPermissao(context)
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun abreDefinicoesApp(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Chamado a cada anúncio BLE com o RSSI do beacon.
     * Detecta movimento pela variação do RSSI e ajusta o GPS.
     */
    fun actualizaRssi(context: Context, mac: String, rssi: Int) {
        val agora = System.currentTimeMillis()
        val hist = historicoRssi.getOrPut(mac) { ArrayDeque() }
        hist.addLast(agora to rssi)
        while (hist.isNotEmpty() && agora - hist.first().first > JANELA_MOVIMENTO_MS) {
            hist.removeFirst()
        }
        if (hist.size < 3) return

        val valores = hist.map { it.second }
        val variacao = valores.max() - valores.min()
        val detectouMovimento = variacao >= LIMIAR_RSSI_MOVIMENTO

        if (detectouMovimento) {
            ultimoMovimentoEm = agora
            if (!emMovimento) {
                emMovimento = true
                // religar/acelerar GPS
                if (modoContinuoActivo) ajustaIntervalo(context, INTERVALO_MOVIMENTO_MS)
                else iniciaModoContinuo(context, INTERVALO_MOVIMENTO_MS)
            }
        } else if (emMovimento) {
            val tempoParado = agora - ultimoMovimentoEm
            when {
                tempoParado > TEMPO_GPS_REDUZIDO_MS -> {
                    emMovimento = false
                    paraModoContinuo(context)  // GPS desligado
                }
                tempoParado > JANELA_MOVIMENTO_MS -> {
                    emMovimento = false
                    ajustaIntervalo(context, INTERVALO_PARADO_MS)  // GPS lento
                }
            }
        }
    }

    @Synchronized
    private fun ajustaIntervalo(context: Context, intervaloMs: Long) {
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val listener = listenerContinuo ?: return
        val provider = when {
            gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        try { gestor.removeUpdates(listener) } catch (_: Exception) {}
        try {
            gestor.requestLocationUpdates(provider, intervaloMs, DISTANCIA_M,
                listener, Looper.getMainLooper())
        } catch (_: SecurityException) {}
    }

    @Synchronized
    fun iniciaModoContinuo(context: Context, intervaloMs: Long = INTERVALO_MOVIMENTO_MS) {
        if (modoContinuoActivo) return
        if (!temPermissao(context)) return
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val provider = when {
            gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) { ultimaLocalizacao = loc }
            @Deprecated("") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        try {
            gestor.requestLocationUpdates(provider, intervaloMs, DISTANCIA_M,
                listener, Looper.getMainLooper())
            listenerContinuo = listener
            modoContinuoActivo = true
        } catch (_: SecurityException) {}
    }

    @Synchronized
    fun paraModoContinuo(context: Context) {
        if (!modoContinuoActivo) return
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        listenerContinuo?.let { try { gestor.removeUpdates(it) } catch (_: Exception) {} }
        listenerContinuo = null
        modoContinuoActivo = false
    }

    suspend fun obtemLocalizacaoAtual(context: Context): Pair<Double, Double>? {
        if (!temPermissao(context)) return null
        val cache = ultimaLocalizacao
        if (cache != null && System.currentTimeMillis() - cache.time < MAX_IDADE_CACHE_MS) {
            return cache.latitude to cache.longitude
        }
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<Location?>()
        val cancelSignal = android.os.CancellationSignal()
        try {
            androidx.core.location.LocationManagerCompat.getCurrentLocation(
                gestor, provider, cancelSignal, java.util.concurrent.Executor { it.run() }
            ) { loc -> deferred.complete(loc) }
        } catch (_: SecurityException) { return null }
        val loc = kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        if (loc == null) cancelSignal.cancel()
        loc?.let { ultimaLocalizacao = it }
        return loc?.let { it.latitude to it.longitude }
    }
}
