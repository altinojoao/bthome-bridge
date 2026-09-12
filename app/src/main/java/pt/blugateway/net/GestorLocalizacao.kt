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
 * GPS adaptativo em duas camadas:
 *
 * CAMADA 1 -- GPS activo: usa a POSIÇÃO REAL para decidir se há
 * movimento -- imune a variações de RSSI por giro do corpo ou
 * reflexões. Se a posição não muda > RAIO_PARADO_M em
 * TEMPO_PARADO_LENTO_MS → reduz GPS para 30s. Se não muda em
 * TEMPO_PARADO_DESLIGA_MS → desliga GPS.
 *
 * CAMADA 2 -- GPS desligado: usa RSSI como proxy, mas com critério
 * muito conservador (variação de 10 dBm consistente ao longo de 90s)
 * para evitar religar o GPS a cada giro do corpo. Só religar após
 * confirmação em duas janelas seguidas.
 *
 * Resultado: GPS activo apenas durante deslocações reais, sem falsos
 * positivos por postura/reflexões. Poupança ~95% face ao modo contínuo.
 */
object GestorLocalizacao {

    private const val TIMEOUT_MS = 30_000L
    private const val MAX_IDADE_CACHE_MS = 8_000L

    // Camada 1: detecção de movimento por posição GPS
    private const val RAIO_PARADO_M = 30.0          // < 30m em X tempo = parado
    private const val TEMPO_PARADO_LENTO_MS = 3 * 60_000L   // 3min → GPS a 30s
    private const val TEMPO_PARADO_DESLIGA_MS = 8 * 60_000L // 8min → GPS desligado

    // Camada 2: RSSI conservador (GPS desligado)
    private const val LIMIAR_RSSI_MOVIMENTO_DBM = 10  // variação mínima credível
    private const val JANELA_RSSI_MS = 90_000L         // janela de análise
    private const val FRACAO_CONSISTENCIA = 0.60       // variação em ≥60% da janela
    private const val CONFIRMACOES_PARA_RELIGAR = 2    // janelas seguidas antes de religar

    private const val INTERVALO_MOVIMENTO_MS = 1_000L
    private const val INTERVALO_LENTO_MS = 30_000L
    private const val DISTANCIA_M = 0f

    @Volatile private var ultimaLocalizacao: Location? = null
    @Volatile private var posicaoAncora: Location? = null   // posição quando parou
    @Volatile private var tempoAncora: Long = 0L            // quando ficou parado
    @Volatile private var modoContinuoActivo = false
    @Volatile private var modoLento = false                  // GPS a 30s
    private var listenerContinuo: LocationListener? = null

    // Camada 2: histórico RSSI por MAC
    private val historicoRssi = java.util.concurrent.ConcurrentHashMap<
        String, ArrayDeque<Pair<Long, Int>>>()
    @Volatile private var confirmacoesPendentes = 0

    fun temPermissao(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun temPermissaoSegundoPlano(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return temPermissao(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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
     * Camada 1: chamado a cada nova posição GPS.
     * Usa a posição real -- imune a RSSI -- para decidir o estado.
     */
    private fun processaPosicaoGps(context: Context, loc: Location) {
        ultimaLocalizacao = loc
        val agora = System.currentTimeMillis()
        val ancora = posicaoAncora

        if (ancora == null) {
            // primeira posição -- inicializar âncora
            posicaoAncora = loc
            tempoAncora = agora
            return
        }

        val deslocacao = ancora.distanceTo(loc)  // metros desde a âncora

        if (deslocacao > RAIO_PARADO_M) {
            // movimento real confirmado pelo GPS → resetar âncora, GPS rápido
            posicaoAncora = loc
            tempoAncora = agora
            confirmacoesPendentes = 0
            if (modoLento) {
                modoLento = false
                ajustaIntervalo(context, INTERVALO_MOVIMENTO_MS)
            }
        } else {
            // dentro do raio -- verificar há quanto tempo
            val tempoParado = agora - tempoAncora
            when {
                tempoParado >= TEMPO_PARADO_DESLIGA_MS && !modoLento -> {
                    // parado há muito -- desligar GPS completamente
                    paraModoContinuo(context)
                }
                tempoParado >= TEMPO_PARADO_LENTO_MS && !modoLento -> {
                    // parado há algum tempo -- reduzir GPS para 30s
                    modoLento = true
                    ajustaIntervalo(context, INTERVALO_LENTO_MS)
                }
                tempoParado >= TEMPO_PARADO_DESLIGA_MS && modoLento -> {
                    paraModoContinuo(context)
                }
            }
        }
    }

    /**
     * Camada 2: RSSI conservador quando o GPS está desligado.
     * Chamado a cada anúncio BLE -- ignora variações de postura/reflexão.
     */
    fun actualizaRssi(context: Context, mac: String, rssi: Int) {
        // Se GPS activo, não precisamos do RSSI para detectar movimento
        if (modoContinuoActivo) return

        val agora = System.currentTimeMillis()
        val hist = historicoRssi.getOrPut(mac) { ArrayDeque() }
        hist.addLast(agora to rssi)
        while (hist.isNotEmpty() && agora - hist.first().first > JANELA_RSSI_MS) {
            hist.removeFirst()
        }
        if (hist.size < 15) return  // precisa de pelo menos 15 amostras (90s / ~6s)

        val movimentoDetectado = detectaMovimentoRssiConservador(hist.map { it.second })
        if (movimentoDetectado) {
            confirmacoesPendentes++
            if (confirmacoesPendentes >= CONFIRMACOES_PARA_RELIGAR) {
                // movimento confirmado em várias janelas -- religar GPS
                confirmacoesPendentes = 0
                posicaoAncora = null  // resetar âncora para nova detecção
                tempoAncora = System.currentTimeMillis()
                modoLento = false
                iniciaModoContinuo(context, INTERVALO_MOVIMENTO_MS)
            }
        } else {
            confirmacoesPendentes = 0
        }
    }

    /**
     * Detecta movimento no RSSI com critério conservador:
     * a variação tem de ser grande (≥10 dBm) E consistente ao longo
     * da janela (não apenas um pico de giro de corpo).
     */
    private fun detectaMovimentoRssiConservador(valores: List<Int>): Boolean {
        if (valores.size < 15) return false
        if (valores.max() - valores.min() < LIMIAR_RSSI_MOVIMENTO_DBM) return false
        // Verificar consistência: em sub-janelas de 20 amostras, quantas
        // têm variação >= metade do limiar?
        val subJanela = 20
        val variacoes = (0..valores.size - subJanela step 5).map { i ->
            val sub = valores.subList(i, i + subJanela)
            sub.max() - sub.min()
        }
        if (variacoes.isEmpty()) return false
        val fracaoAlta = variacoes.count { it >= LIMIAR_RSSI_MOVIMENTO_DBM / 2 }.toDouble() / variacoes.size
        return fracaoAlta >= FRACAO_CONSISTENCIA
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
        try { gestor.removeUpdates(listener) } catch (e: Exception) {}
        try {
            gestor.requestLocationUpdates(
                provider, intervaloMs, DISTANCIA_M, listener, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {}
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
            override fun onLocationChanged(loc: Location) {
                processaPosicaoGps(context, loc)
            }
            @Deprecated("") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }
        try {
            gestor.requestLocationUpdates(
                provider, intervaloMs, DISTANCIA_M, listener, Looper.getMainLooper()
            )
            listenerContinuo = listener
            modoContinuoActivo = true
        } catch (e: SecurityException) {}
    }

    @Synchronized
    fun paraModoContinuo(context: Context) {
        if (!modoContinuoActivo) return
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        listenerContinuo?.let { try { gestor.removeUpdates(it) } catch (e: Exception) {} }
        listenerContinuo = null
        modoContinuoActivo = false
        modoLento = false
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
        } catch (e: SecurityException) { return null }
        val loc = kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        if (loc == null) cancelSignal.cancel()
        loc?.let { ultimaLocalizacao = it }
        return loc?.let { it.latitude to it.longitude }
    }
}
