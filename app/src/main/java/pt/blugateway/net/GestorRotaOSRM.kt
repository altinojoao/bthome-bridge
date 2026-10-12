package pt.blugateway.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pt.blugateway.data.PontoTemplate

/**
 * Uma rota calculada pelo OSRM entre dois pontos -- pode haver
 * varias alternativas para a mesma origem/destino (ver
 * calculaRotas), o utilizador escolhe qual usar como template.
 */
data class RotaCalculada(
    val pontos: List<PontoTemplate>,
    val distanciaMetros: Double,
    val duracaoSegundos: Double,
    val origemExata: PontoTemplate? = null,
    val destinoExato: PontoTemplate? = null,
    // pontos de passagem intermédios (pinos laranja) definidos pelo
    // utilizador para forçar variantes de rota -- guardados para
    // restaurar ao reabrir o cenário para editar
    val waypointsIntermédios: List<PontoTemplate> = emptyList()
)

sealed class ResultadoCalculoRota {
    data class Disponivel(val rotas: List<RotaCalculada>) : ResultadoCalculoRota()
    object SemRota : ResultadoCalculoRota()
    data class Erro(val mensagem: String) : ResultadoCalculoRota()
}

/**
 * Cliente do servidor publico de demonstracao do OSRM
 * (router.project-osrm.org) -- gratuito, sem chave, mas com uma
 * politica de uso que exige um User-Agent identificando a app e
 * pede para nao ser usado excessivamente. Adequado ao uso pontual
 * desta funcionalidade (o utilizador pede uma rota ao criar um
 * cenario, nao repetidamente em fundo).
 *
 * As coordenadas na resposta do OSRM vem em GeoJSON, ou seja
 * [longitude, latitude] -- invertido em relacao a PontoTemplate
 * (lat, lon), usado no resto desta app. A conversao acontece aqui,
 * uma unica vez, para o resto do codigo nunca ter de se preocupar
 * com isso.
 */
object GestorRotaOSRM {

    private const val URL_BASE = "https://router.project-osrm.org/route/v1/driving"
    private const val USER_AGENT = "BluGatewayApp/1.0 (+https://github.com/altinojoao/bthome-bridge)"

    private val cliente = OkHttpClient.Builder().build()

    /**
     * Pede ao OSRM ate 3 rotas alternativas entre origem e destino
     * -- o profile 'driving' e' usado sempre (a app nao distingue
     * modos de transporte, um trajeto de beacon pode ser feito a pe
     * ou de carro; 'driving' da' a rota mais generica pela rede
     * viaria existente).
     */
    suspend fun calculaRotas(
        origemLat: Double,
        origemLon: Double,
        destinoLat: Double,
        destinoLon: Double,
        waypoints: List<PontoTemplate> = emptyList()
    ): ResultadoCalculoRota = withContext(Dispatchers.IO) {
        val origemExata = PontoTemplate(origemLat, origemLon)
        val destinoExato = PontoTemplate(destinoLat, destinoLon)
        try {
            // Construir a lista de coordenadas: origem, waypoints, destino
            val coordenadas = buildString {
                append("$origemLon,$origemLat")
                waypoints.forEach { append(";${it.lon},${it.lat}") }
                append(";$destinoLon,$destinoLat")
            }
            // Com waypoints, o OSRM não suporta alternatives --
            // cada waypoint define um troço fixo, sem margem para variantes.
            // Sem waypoints, pedir sempre 3 alternativas.
            val params = if (waypoints.isEmpty())
                "?geometries=geojson&overview=full&alternatives=3"
            else
                "?geometries=geojson&overview=full"
            val url = "$URL_BASE/$coordenadas$params"
            val pedido = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    return@withContext ResultadoCalculoRota.Erro("OSRM respondeu ${resposta.code}")
                }
                val corpo = resposta.body?.string()
                    ?: return@withContext ResultadoCalculoRota.Erro("Resposta vazia")

                val json = JSONObject(corpo)
                val codigo = json.optString("code", "")
                if (codigo == "NoRoute") return@withContext ResultadoCalculoRota.SemRota
                if (codigo != "Ok") {
                    return@withContext ResultadoCalculoRota.Erro(json.optString("message", codigo))
                }

                val arrRotas = json.optJSONArray("routes")
                    ?: return@withContext ResultadoCalculoRota.SemRota

                val rotas = (0 until arrRotas.length()).mapNotNull { i ->
                    val rota = arrRotas.getJSONObject(i)
                    val geometria = rota.optJSONObject("geometry") ?: return@mapNotNull null
                    val coords = geometria.optJSONArray("coordinates") ?: return@mapNotNull null
                    val pontos = (0 until coords.length()).map { j ->
                        val par = coords.getJSONArray(j)
                        PontoTemplate(lat = par.getDouble(1), lon = par.getDouble(0))
                    }
                    RotaCalculada(
                        pontos = pontos,
                        distanciaMetros = rota.optDouble("distance", 0.0),
                        duracaoSegundos = rota.optDouble("duration", 0.0),
                        origemExata = origemExata,
                        destinoExato = destinoExato,
                        waypointsIntermédios = waypoints
                    )
                }

                if (rotas.isEmpty()) ResultadoCalculoRota.SemRota
                else ResultadoCalculoRota.Disponivel(rotas)
            }
        } catch (e: Exception) {
            ResultadoCalculoRota.Erro(e.message ?: "Erro de rede desconhecido")
        }
    }
}
