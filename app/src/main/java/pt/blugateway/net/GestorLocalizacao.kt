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
 * Gere a localizacao GPS para dois usos distintos:
 *
 * 1. Pedido pontual (obtemLocalizacaoAtual): para incluir {lat}/{lon}
 *    nas acoes de um comando -- usa a ultima localizacao em cache se
 *    for recente o suficiente, senao aguarda uma leitura nova.
 *
 * 2. Modo continuo (iniciaModoContinuo / paraModoContinuo): ligado
 *    enquanto pelo menos um comando tem modoBeaconTrajeto=true. Mantem
 *    o GPS activo e em aquecimento, eliminando o "GPS frio" que causava
 *    erros de 50-300m na primeira leitura apos um pedido unitario.
 *    Cada ponto gravado pelo GestorTrajeto usa a ultima leitura em
 *    cache, que ja estara estabilizada (erro tipico < 10m em campo
 *    aberto, < 40m em zona urbana).
 *
 * Usa LocationManager directamente (nao FusedLocationProviderClient)
 * para manter compatibilidade com dispositivos sem Google Play Services.
 */
object GestorLocalizacao {

    private const val TIMEOUT_MS = 30_000L
    // Maxima idade de uma leitura em cache para ser considerada
    // "recente" num pedido pontual (obtemLocalizacaoAtual)
    private const val MAX_IDADE_CACHE_MS = 8_000L
    // Intervalo minimo entre actualizacoes no modo continuo -- 1s e
    // suficiente para manter o GPS "aquecido" sem drenar a bateria
    private const val INTERVALO_CONTINUO_MS = 1_000L
    private const val DISTANCIA_CONTINUO_M = 0f

    @Volatile private var ultimaLocalizacao: Location? = null
    @Volatile private var modoContinuoActivo = false
    private var listenerContinuo: LocationListener? = null

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
     * Liga o GPS em modo continuo -- chamado pelo ServicoGateway
     * quando detecta pelo menos um comando com modoBeaconTrajeto=true.
     * Mantem o GPS "aquecido" para que cada ponto gravado use uma
     * leitura ja estabilizada em vez de acordar o GPS de raiz.
     * Idempotente: chamadas repetidas nao criam listeners duplicados.
     */
    @Synchronized
    fun iniciaModoContinuo(context: Context) {
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
            gestor.requestLocationUpdates(
                provider, INTERVALO_CONTINUO_MS, DISTANCIA_CONTINUO_M,
                listener, Looper.getMainLooper()
            )
            listenerContinuo = listener
            modoContinuoActivo = true
        } catch (_: SecurityException) {}
    }

    /** Para o modo continuo -- chamado quando ja nao ha comandos com
     *  modoBeaconTrajeto=true, para poupar bateria. */
    @Synchronized
    fun paraModoContinuo(context: Context) {
        if (!modoContinuoActivo) return
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        listenerContinuo?.let {
            try { gestor.removeUpdates(it) } catch (_: Exception) {}
        }
        listenerContinuo = null
        modoContinuoActivo = false
    }

    /**
     * Devolve (latitude, longitude) ou null.
     * Se houver uma leitura em cache recente (< 8s), usa-a
     * directamente sem acordar o GPS -- e muito mais rapido e preciso
     * porque o GPS ja estava aquecido no modo continuo.
     * Se nao houver cache recente, faz um pedido unico com timeout.
     */
    suspend fun obtemLocalizacaoAtual(context: Context): Pair<Double, Double>? {
        if (!temPermissao(context)) return null

        // Usar cache recente se disponivel (modo continuo activo)
        val cache = ultimaLocalizacao
        if (cache != null) {
            val idadeMs = System.currentTimeMillis() - cache.time
            if (idadeMs < MAX_IDADE_CACHE_MS) {
                return cache.latitude to cache.longitude
            }
        }

        // Fallback: pedido unico (para quando o modo continuo nao esta activo)
        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        val deferred = kotlinx.coroutines.CompletableDeferred<Location?>()
        val cancelSignal = android.os.CancellationSignal()
        val executorImediato = java.util.concurrent.Executor { it.run() }
        try {
            androidx.core.location.LocationManagerCompat.getCurrentLocation(
                gestor, provider, cancelSignal, executorImediato
            ) { loc -> deferred.complete(loc) }
        } catch (_: SecurityException) { return null }

        val loc = kotlinx.coroutines.withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        if (loc == null) cancelSignal.cancel()
        loc?.let { ultimaLocalizacao = it }
        return loc?.let { it.latitude to it.longitude }
    }
}
