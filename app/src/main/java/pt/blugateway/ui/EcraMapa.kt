package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import pt.blugateway.R
import pt.blugateway.data.Comando
import pt.blugateway.data.PontoTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/* Paleta fixa de cores, uma por comando (por indice na lista, ciclica
   se houver mais comandos que cores) -- so para diferenciar
   visualmente os tracados no mapa. */
private val CORES_TRAJETO = listOf(
    "#2BA6E0", "#E0A62B", "#4CAF50", "#E0522B", "#9C6ADE", "#2BE0C7"
)

/* Ecra de mapa: mostra o historico de trajeto de todos os comandos
   com pontos guardados, sobrepostos, cada um com uma cor propria.
   Usa um WebView com Leaflet.js e tiles OpenStreetMap.

   Usa Dialog() diretamente, NAO AlertDialog -- a documentacao
   oficial do Compose recomenda Dialog com conteudo proprio para
   qualquer coisa mais complexa que os slots rigidos de
   title/text/buttons do AlertDialog cobrem, e AndroidView (WebView)
   dentro de AlertDialog e um cenario menos testado/mais restrito
   pela propria Window/Surface que o AlertDialog cria internamente.

   Os pontos sao enviados para dentro da pagina via
   evaluateJavascript() depois da pagina ja estar carregada
   (onPageFinished), nunca embutidos no HTML inicial. */
@Composable
fun EcraMapa(
    comandos: List<Comando>,
    vm: GatewayViewModel,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var confirmaLimpar by remember { mutableStateOf<String?>(null) }
    var paginaCarregada by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var erroMapa by remember { mutableStateOf<String?>(null) }
    var soUltimaViagem by remember { mutableStateOf(false) }

    val comandosComHistorico = remember(comandos) {
        comandos.mapNotNull { c ->
            val pontos = vm.historicoTrajeto(c.mac)
            if (pontos.isEmpty()) null else c to pontos
        }
    }

    val jsonTrajetos = remember(comandosComHistorico) {
        construirJsonTrajetos(comandosComHistorico)
    }

    LaunchedEffect(jsonTrajetos, paginaCarregada, soUltimaViagem) {
        if (paginaCarregada) {
            val webView = webViewRef ?: return@LaunchedEffect
            webView.evaluateJavascript("defineMostrarSoUltimaViagem($soUltimaViagem);", null)
            val script = "desenhaTrajetos(${JSONObject.quote(jsonTrajetos)});"
            webView.evaluateJavascript(script, null)
        }
    }

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.mapa_trajeto),
                        color = cores.tinta,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFecha) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.mapa_so_ultima_viagem),
                        color = cores.suave,
                        fontSize = 10.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.size(width = 38.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Switch(
                            checked = soUltimaViagem,
                            onCheckedChange = { soUltimaViagem = it },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }

                MapaWebView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(cores.elevado),
                    onPaginaCarregada = { webView ->
                        webViewRef = webView
                        paginaCarregada = true
                    },
                    onErro = { msg ->
                        if (erroMapa == null) erroMapa = msg
                    }
                )

                erroMapa?.let { msg ->
                    Text(
                        msg,
                        color = cores.avisoTinta,
                        fontSize = 9.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }

                comandosComHistorico.forEach { (c, _) ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.nome, color = cores.suave, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmaLimpar = c.mac }) {
                            Text(stringResource(R.string.limpar), color = cores.avisoTinta, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    confirmaLimpar?.let { mac ->
        AlertDialog(
            onDismissRequest = { confirmaLimpar = null },
            title = { Text(stringResource(R.string.limpar_trajeto_q)) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    vm.limpaTrajeto(mac)
                    confirmaLimpar = null
                }) { Text(stringResource(R.string.limpar)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmaLimpar = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MapaWebView(
    modifier: Modifier = Modifier,
    onPaginaCarregada: (WebView) -> Unit,
    onErro: (String) -> Unit
) {
    AndroidView(
        modifier = modifier,
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
                        onPaginaCarregada(view)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true || request?.url?.toString()?.contains("android_asset") == true) {
                            onErro("onReceivedError: ${request?.url} -- ${error?.description}")
                        }
                    }

                    // O WebView so sabe navegar dentro de si mesmo para
                    // http/https -- qualquer outro esquema (geo:, tel:,
                    // mailto:, intent:) e ignorado silenciosamente por
                    // omissao. Os links "abrir localizacao" dos popups do
                    // mapa usam geo:, por isso precisam de ser
                    // explicitamente reencaminhados para uma Activity
                    // externa via Intent -- e o unico jeito de o
                    // utilizador escolher a app de mapas que prefere.
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url ?: return false
                        if (url.scheme == "http" || url.scheme == "https") return false
                        return try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            view?.context?.startActivity(intent)
                            true
                        } catch (e: android.content.ActivityNotFoundException) {
                            onErro("Nenhuma app instalada consegue abrir: $url")
                            true
                        }
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        if (msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            onErro("console.error: ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                        }
                        return true
                    }
                }
                loadUrl("file:///android_asset/leaflet/mapa.html")
            }
        }
    )
}

/* Constroi o JSON (como String, ainda por injetar no WebView) com o
   trajeto de cada comando -- uma cor fixa por posicao na lista,
   ciclica. */
private fun construirJsonTrajetos(comandosComHistorico: List<Pair<Comando, List<PontoTrajeto>>>): String {
    val arr = JSONArray()
    comandosComHistorico.forEachIndexed { indice, par ->
        val (comando, pontos) = par
        val obj = JSONObject()
        obj.put("nome", comando.nome)
        obj.put("cor", CORES_TRAJETO[indice % CORES_TRAJETO.size])
        val arrPontos = JSONArray()
        pontos.forEach { p ->
            arrPontos.put(JSONObject().apply {
                put("lat", p.latitude)
                put("lon", p.longitude)
                put("timestamp", p.timestamp)
                put("origem", p.origem.name)
            })
        }
        obj.put("pontos", arrPontos)
        arr.put(obj)
    }
    return arr.toString()
}
