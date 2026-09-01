package pt.blugateway.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor

/**
 * Obtem uma unica localizacao atual, sob pedido, para incluir nos
 * marcadores {lat}/{lon} de uma acao. So e chamado quando o comando
 * que disparou o clique tem incluirLocalizacao=true -- e um opt-in
 * EXPLICITO por comando, nunca ligado por omissao (ver Comando.
 * incluirLocalizacao em Modelos.kt).
 *
 * Usa LocationManagerCompat em vez de FusedLocationProviderClient
 * (Play Services) de proposito: mantem a app a funcionar em
 * dispositivos sem Google Play Services, e nao adiciona uma
 * dependencia pesada so para isto.
 */
object GestorLocalizacao {

    private const val TIMEOUT_MS = 8_000L

    fun temPermissao(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Devolve (latitude, longitude) ou null se a permissao nao foi
     * concedida, o GPS/rede estiverem desligados, ou nao houver
     * fix dentro de TIMEOUT_MS. Nunca lanca excecao para o chamador
     * -- a ausencia de localizacao nao deve impedir o resto da acao
     * de correr (os marcadores {lat}/{lon} ficam so vazios).
     */
    suspend fun obtemLocalizacaoAtual(context: Context): Pair<Double, Double>? {
        if (!temPermissao(context)) return null

        val gestor = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = when {
            gestor.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            gestor.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        val deferred = CompletableDeferred<Location?>()
        val cancelSignal = CancellationSignal()
        val executorImediato = Executor { it.run() }

        try {
            LocationManagerCompat.getCurrentLocation(
                gestor, provider, cancelSignal, executorImediato
            ) { localizacao -> deferred.complete(localizacao) }
        } catch (e: SecurityException) {
            // permissao revogada entre a verificacao acima e esta
            // chamada (janela de corrida rara, mas possivel)
            return null
        }

        val localizacao = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        if (localizacao == null) cancelSignal.cancel()
        return localizacao?.let { it.latitude to it.longitude }
    }
}
