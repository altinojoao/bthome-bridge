package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pt.blugateway.R
import pt.blugateway.data.PontoTemplate
import pt.blugateway.net.GestorRotaOSRM
import pt.blugateway.net.ResultadoCalculoRota
import pt.blugateway.net.RotaCalculada
import pt.blugateway.ui.theme.LocalCoresGateway

private sealed class EstadoRota {
    object AEscolherPontos : EstadoRota()
    object ACalcular : EstadoRota()
    data class Alternativas(val rotas: List<RotaCalculada>) : EstadoRota()
    object SemRota : EstadoRota()
    data class Erro(val mensagem: String) : EstadoRota()
}

/**
 * Escolher origem e destino no mapa (um toque cada) e calcular
 * alternativas de rota via OSRM entre eles -- estilo Waze. Usa
 * mapa_rota.html (dedicado, toque simples em vez de arrasto,
 * diferente do modo de desenho livre em MapaDesenhoTrajeto).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapaRotaTrajeto(
    modifier: Modifier = Modifier,
    templateExistente: List<PontoTemplate> = emptyList(),
    origemExistente: PontoTemplate? = null,
    destinoExistente: PontoTemplate? = null,
    waypointsExistentes: List<PontoTemplate> = emptyList(),
    onRotaEscolhida: (RotaCalculada) -> Unit,
    onErro: (String) -> Unit = {}
) {
    val cores = LocalCoresGateway.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val escopo = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var paginaCarregada by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf<EstadoRota>(EstadoRota.AEscolherPontos) }
    var origemAtual by remember { mutableStateOf<PontoTemplate?>(null) }
    var destinoAtual by remember { mutableStateOf<PontoTemplate?>(null) }
    // waypoints actuais -- actualizados sempre que o JS notifica pontos,
    // independentemente de o utilizador ter tocado num botão de rota
    var waypointsActuais by remember { mutableStateOf<List<PontoTemplate>>(emptyList()) }
    var existenteEnviado by remember { mutableStateOf(false) }

    // Ao abrir: centra na localizacao atual (senao o mapa abre no
    // meio do oceano, em [0,0]) e, se ja houver um template gravado
    // (edicao de um cenario existente), desenha-o.
    LaunchedEffect(paginaCarregada) {
        if (!paginaCarregada) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect

        if (templateExistente.isNotEmpty() && !existenteEnviado) {
            existenteEnviado = true
            val json = JSONArray().apply {
                templateExistente.forEach { p ->
                    put(JSONObject().apply {
                        put("lat", p.lat)
                        put("lon", p.lon)
                    })
                }
            }.toString()
            val origemJson = origemExistente?.let {
                JSONObject().apply { put("lat", it.lat); put("lon", it.lon) }.toString()
            }
            val destinoJson = destinoExistente?.let {
                JSONObject().apply { put("lat", it.lat); put("lon", it.lon) }.toString()
            }
            val origemArg = if (origemJson != null) JSONObject.quote(origemJson) else "null"
            val destinoArg = if (destinoJson != null) JSONObject.quote(destinoJson) else "null"
            // Waypoints intermédios (pinos laranja)
            val waypointsJson = if (waypointsExistentes.isNotEmpty()) {
                JSONArray().apply {
                    waypointsExistentes.forEach { w ->
                        put(JSONObject().apply { put("lat", w.lat); put("lon", w.lon) })
                    }
                }.toString()
            } else null
            val waypointsArg = if (waypointsJson != null) JSONObject.quote(waypointsJson) else "null"
            webView.evaluateJavascript(
                "defineTrajetoExistente(${JSONObject.quote(json)}, $origemArg, $destinoArg, $waypointsArg);",
                null
            )
        } else {
            pt.blugateway.net.GestorLocalizacao.obtemLocalizacaoAtual(contexto)?.let { (lat, lon) ->
                webView.evaluateJavascript("centraNaLocalizacao($lat, $lon, 14);", null)
            }
        }
    }

    Column(modifier) {
        val estadoAtual = estado
        when (estadoAtual) {
            is EstadoRota.AEscolherPontos -> {
                Text(
                    if (origemAtual == null) stringResource(R.string.rota_toca_origem)
                    else stringResource(R.string.rota_toca_destino),
                    color = cores.suave,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            is EstadoRota.ACalcular -> {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rota_a_calcular), color = cores.suave, fontSize = 11.sp)
                }
            }
            is EstadoRota.Alternativas -> {
                Text(
                    stringResource(R.string.rota_toca_alternativa, estadoAtual.rotas.size),
                    color = cores.suave,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            is EstadoRota.SemRota -> {
                Text(
                    stringResource(R.string.rota_sem_resultado),
                    color = cores.avisoTinta,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            is EstadoRota.Erro -> {
                Text(
                    stringResource(R.string.rota_erro, estadoAtual.mensagem),
                    color = cores.avisoTinta,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            webViewRef = view
                            paginaCarregada = true
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                onErro("onReceivedError: ${request.url} -- ${error?.description}")
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return false
                            if (url.scheme != "blugateway-rota") return false

                            if (url.host == "pontos") {
                                val dados = url.getQueryParameter("dados") ?: return true
                                try {
                                    val json = JSONObject(dados)
                                    val jOrigem = json.optJSONObject("origem")
                                    val jDestino = json.optJSONObject("destino")
                                    val jWaypoints = json.optJSONArray("waypoints")
                                    origemAtual = jOrigem?.let {
                                        PontoTemplate(it.getDouble("lat"), it.getDouble("lon"))
                                    }
                                    destinoAtual = jDestino?.let {
                                        PontoTemplate(it.getDouble("lat"), it.getDouble("lon"))
                                    }
                                    val waypoints = if (jWaypoints != null) {
                                        (0 until jWaypoints.length()).map { i ->
                                            val w = jWaypoints.getJSONObject(i)
                                            PontoTemplate(w.getDouble("lat"), w.getDouble("lon"))
                                        }
                                    } else emptyList()
                                    // guardar os waypoints actuais no estado interno --
                                    // independentemente de o utilizador tocar num botão
                                    waypointsActuais = waypoints
                                    val origem = origemAtual
                                    val destino = destinoAtual
                                    if (origem != null && destino != null) {
                                        estado = EstadoRota.ACalcular
                                        escopo.launch {
                                            val resultado = GestorRotaOSRM.calculaRotas(
                                                origem.lat, origem.lon, destino.lat, destino.lon,
                                                waypoints
                                            )
                                            estado = when (resultado) {
                                                is ResultadoCalculoRota.Disponivel -> EstadoRota.Alternativas(resultado.rotas)
                                                is ResultadoCalculoRota.SemRota -> EstadoRota.SemRota
                                                is ResultadoCalculoRota.Erro -> EstadoRota.Erro(resultado.mensagem)
                                            }
                                            val e = estado
                                            if (e is EstadoRota.Alternativas) {
                                                webViewRef?.evaluateJavascript(
                                                    "desenhaAlternativas(${JSONObject.quote(construirJsonAlternativas(e.rotas))});",
                                                    null
                                                )
                                            }
                                        }
                                    } else {
                                        estado = EstadoRota.AEscolherPontos
                                    }
                                } catch (e: Exception) {
                                    onErro("erro a processar pontos: ${e.message}")
                                }
                            } else if (url.host == "escolhe") {
                                val indice = url.getQueryParameter("indice")?.toIntOrNull() ?: return true
                                val e = estado
                                if (e is EstadoRota.Alternativas) {
                                    e.rotas.getOrNull(indice)?.let { rota ->
                                        // incluir os waypoints actuais na rota escolhida
                                        onRotaEscolhida(rota.copy(waypointsIntermédios = waypointsActuais))
                                    }
                                    webViewRef?.evaluateJavascript("marcaAlternativaSelecionada($indice);", null)
                                }
                            }
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/leaflet/mapa_rota.html")
                }
            }
        )
    }
}

private fun construirJsonAlternativas(rotas: List<RotaCalculada>): String {
    val arr = JSONArray()
    rotas.forEach { rota ->
        val obj = JSONObject()
        val arrPontos = JSONArray()
        rota.pontos.forEach { p ->
            arrPontos.put(JSONObject().apply {
                put("lat", p.lat)
                put("lon", p.lon)
            })
        }
        obj.put("pontos", arrPontos)
        arr.put(obj)
    }
    return arr.toString()
}
