package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import pt.blugateway.R
import pt.blugateway.data.PontoTemplate
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Mapa de desenho livre -- alternativa a escolher uma viagem ja
 * gravada: o utilizador arrasta o dedo sobre o mapa para desenhar a
 * dedo o proprio trajeto, que passa a ser o template do cenario.
 *
 * Usa mapa_desenho.html (dedicado, dragging do Leaflet desativado
 * enquanto em "modo desenho"). O resultado chega via o mesmo padrao
 * de esquema de URL customizado ja usado nos outros mapas deste
 * projeto (blugateway-desenho://atualizado?pontos=...), intercetado
 * em shouldOverrideUrlLoading.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapaDesenhoTrajeto(
    modifier: Modifier = Modifier,
    pontos: List<PontoTemplate>,
    onPontosAlterados: (List<PontoTemplate>) -> Unit,
    onErro: (String) -> Unit = {}
) {
    val cores = LocalCoresGateway.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var paginaCarregada by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var modoDesenhoAtivo by remember { mutableStateOf(true) }
    // garante que o template existente so' e' enviado uma vez ao JS
    // -- reenviar a cada recomposicao apagaria o que o utilizador
    // estivesse a desenhar por cima
    var existenteEnviado by remember { mutableStateOf(false) }

    LaunchedEffect(paginaCarregada, modoDesenhoAtivo) {
        if (paginaCarregada) {
            webViewRef?.evaluateJavascript("alternaModoDesenho($modoDesenhoAtivo);", null)
        }
    }

    // Ao abrir: centra na localizacao atual (senao o mapa abre no
    // meio do oceano, em [0,0]) e, se ja houver um template gravado
    // (edicao de um cenario existente), desenha-o para o utilizador
    // ver o que estava definido.
    LaunchedEffect(paginaCarregada) {
        if (!paginaCarregada) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect

        if (pontos.isNotEmpty() && !existenteEnviado) {
            existenteEnviado = true
            val json = org.json.JSONArray().apply {
                pontos.forEach { p ->
                    put(org.json.JSONObject().apply {
                        put("lat", p.lat)
                        put("lon", p.lon)
                    })
                }
            }.toString()
            webView.evaluateJavascript(
                "defineTrajetoExistente(${org.json.JSONObject.quote(json)});",
                null
            )
        } else {
            pt.blugateway.net.GestorLocalizacao.obtemLocalizacaoAtual(contexto)?.let { (lat, lon) ->
                webView.evaluateJavascript("centraNaLocalizacao($lat, $lon, 15);", null)
            }
        }
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            TextButton(onClick = { modoDesenhoAtivo = !modoDesenhoAtivo }) {
                Text(
                    if (modoDesenhoAtivo) stringResource(R.string.desenho_modo_navegar)
                    else stringResource(R.string.desenho_modo_desenhar),
                    color = cores.azul,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            if (pontos.isNotEmpty()) {
                TextButton(onClick = {
                    onPontosAlterados(emptyList())
                    webViewRef?.evaluateJavascript("limpaDesenho();", null)
                }) {
                    Text(stringResource(R.string.limpar), color = cores.avisoTinta, fontSize = 11.sp)
                }
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
                            if (url.scheme != "blugateway-desenho") return false
                            val jsonPontos = url.getQueryParameter("pontos") ?: return true
                            try {
                                val arr = JSONArray(jsonPontos)
                                val novosPontos = (0 until arr.length()).mapNotNull { i ->
                                    val o = arr.getJSONObject(i)
                                    PontoTemplate(o.getDouble("lat"), o.getDouble("lon"))
                                }
                                onPontosAlterados(novosPontos)
                            } catch (e: Exception) {
                                onErro("erro a processar desenho: ${e.message}")
                            }
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/leaflet/mapa_desenho.html")
                }
            }
        )
    }
}
