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
     * Sem esta permissao concedida ("Permitir sempre" nas Definicoes
     * da app), o modo trajeto por beacon so grava pontos com a app
     * visivel/ecra ligado -- em segundo plano, o pedido de
     * localizacao e bloqueado pelo sistema, mesmo com o servico em
     * primeiro plano ativo (ver AndroidManifest.xml, comentario junto
     * a ACCESS_BACKGROUND_LOCATION). Em versoes anteriores ao Android
     * 10 (API 29), esta permissao nao existe -- ACCESS_FINE_LOCATION
     * sozinha ja da acesso completo, incluindo em segundo plano.
     */
    fun temPermissaoSegundoPlano(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return temPermissao(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * A partir do Android 11, ACCESS_BACKGROUND_LOCATION nao pode ser
     * pedida atraves do dialogo normal de permissoes -- o sistema
     * ignora esse pedido silenciosamente. A unica via é abrir o ecra
     * de Definicoes da propria app e o utilizador escolher "Permitir
     * sempre" manualmente.
     */
    fun abreDefinicoesApp(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
