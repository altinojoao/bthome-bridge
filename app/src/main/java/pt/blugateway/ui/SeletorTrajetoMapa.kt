package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import pt.blugateway.ble.GestorSemelhancaTrajeto
import pt.blugateway.data.Comando
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Uma viagem individual ja identificada e atribuida a um comando de
 * origem -- a unidade que o utilizador escolhe visualmente no mapa
 * como template de um cenario. O id combina o mac do comando com o
 * timestamp do primeiro ponto, unico o suficiente para distinguir
 * viagens dentro desta sessao de selecao (nao persistido).
 */
data class ViagemSelecionavel(
    val id: String,
    val comandoOrigem: Comando,
    val pontos: List<PontoTrajeto>
)

/**
 * Mapa com TODAS as viagens de TODOS os comandos com historico,
 * cada uma como uma linha clicavel -- em vez de escolher uma viagem
 * por uma lista de texto (data/hora, numero de pontos), o
 * utilizador ve a forma real do trajeto no mapa e toca na linha
 * certa. Reutiliza GestorSemelhancaTrajeto.separaEmViagens, ja
 * validado e usado no fluxo anterior de criacao de cenarios.
 *
 * Usa mapa_seletor.html (dedicado, mais simples que mapa.html: sem
 * radar/direcao/"ultima viagem", so linhas clicaveis) e o mesmo
 * padrao de esquema de URL customizado ja usado para os links geo:
 * em EcraMapa.kt -- este projeto nao configura
 * addJavascriptInterface, por isso a comunicacao JS -> Kotlin passa
 * sempre por shouldOverrideUrlLoading.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SeletorTrajetoMapa(
    modifier: Modifier = Modifier,
    comandosComHistorico: List<Pair<Comando, List<PontoTrajeto>>>,
    viagemSelecionadaId: String?,
    onSelecionaViagem: (ViagemSelecionavel) -> Unit,
    onErro: (String) -> Unit = {}
) {
    val cores = LocalCoresGateway.current
    var paginaCarregada by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val viagens = remember(comandosComHistorico) {
        comandosComHistorico.flatMap { (comando, historico) ->
            GestorSemelhancaTrajeto.separaEmViagens(historico).map { pontos ->
                ViagemSelecionavel(
                    id = "${comando.mac}|${pontos.first().timestamp}",
                    comandoOrigem = comando,
                    pontos = pontos
                )
            }
        }
    }

    val jsonViagens = remember(viagens) { construirJsonViagens(viagens) }

    LaunchedEffect(jsonViagens, paginaCarregada, viagemSelecionadaId) {
        if (paginaCarregada) {
            val webView = webViewRef ?: return@LaunchedEffect
            webView.evaluateJavascript(
                "defineViagemSelecionada(${JSONObject.quote(viagemSelecionadaId ?: "")});",
                null
            )
            webView.evaluateJavascript("desenhaViagens(${JSONObject.quote(jsonViagens)});", null)
        }
    }

    AndroidView(
        modifier = modifier.background(cores.elevado),
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

                    // O clique numa linha em mapa_seletor.html navega para
                    // blugateway-viagem://selecionar?id=... -- intercetado
                    // aqui em vez de deixar o WebView tentar (e falhar a)
                    // navegar de verdade, tal como geo: em EcraMapa.kt.
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url ?: return false
                        if (url.scheme != "blugateway-viagem") return false
                        val id = url.getQueryParameter("id") ?: return true
                        viagens.firstOrNull { it.id == id }?.let(onSelecionaViagem)
                        return true
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        if (msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            onErro("console.error: ${msg.message()}")
                        }
                        return true
                    }
                }
                loadUrl("file:///android_asset/leaflet/mapa_seletor.html")
            }
        }
    )
}

private fun construirJsonViagens(viagens: List<ViagemSelecionavel>): String {
    val arr = JSONArray()
    viagens.forEach { viagem ->
        val obj = JSONObject()
        obj.put("id", viagem.id)
        obj.put("nomeComando", viagem.comandoOrigem.nome)
        val arrPontos = JSONArray()
        viagem.pontos.sortedBy { it.timestamp }.forEach { p ->
            arrPontos.put(JSONObject().apply {
                put("lat", p.latitude)
                put("lon", p.longitude)
            })
        }
        obj.put("pontos", arrPontos)
        arr.put(obj)
    }
    return arr.toString()
}
