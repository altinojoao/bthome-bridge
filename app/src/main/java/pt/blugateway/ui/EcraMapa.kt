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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
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
    cenarios: List<pt.blugateway.data.CenarioTrajeto>,
    vm: GatewayViewModel,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var confirmaLimpar by remember { mutableStateOf<String?>(null) }
    var paginaCarregada by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var erroMapa by remember { mutableStateOf<String?>(null) }
    var soUltimaViagem by remember { mutableStateOf(false) }

    // Estado da simulacao integrada
    var cenarioSimulado by remember { mutableStateOf<pt.blugateway.data.CenarioTrajeto?>(null) }
    var simEmExecucao by remember { mutableStateOf(false) }
    var simPct by remember { mutableStateOf(0) }
    var simLimiarAtingido by remember { mutableStateOf(false) }
    var simConcluido by remember { mutableStateOf(false) }

    // Loop de simulacao -- avanca um passo de cada vez no JS do mapa
    LaunchedEffect(simEmExecucao) {
        val c = cenarioSimulado ?: return@LaunchedEffect
        while (simEmExecucao && !simConcluido) {
            kotlinx.coroutines.delay(150L)
            webViewRef?.evaluateJavascript("passoSimulacao(${c.limiarPercentagem});", null)
        }
    }

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
            // desenhaTrajetos(jsonTexto) faz JSON.parse(jsonTexto) dentro do JS,
            // logo tem de receber uma STRING JSON (com aspas), nao o array/objeto
            // injetado cru como literal JavaScript -- daí o quote() aqui.
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
                    },
                    onUrlSimulacao = { host, url ->
                        when (host) {
                            "pct" -> simPct = url.getQueryParameter("v")?.toIntOrNull() ?: simPct
                            "limiar" -> simLimiarAtingido = true
                            "concluido" -> { simEmExecucao = false; simConcluido = true }
                        }
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

                // Painel de simulacao -- aparece so quando um cenario
                // esta selecionado para simular
                val sim = cenarioSimulado
                if (sim != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Nome do cenario a simular
                        Text(
                            "\u25B6 ${sim.nome}",
                            color = cores.azul,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        // Percentagem em tempo real
                        if (simPct > 0 && !simConcluido) {
                            Text(
                                "$simPct%",
                                color = if (simLimiarAtingido) cores.ok else cores.tinta,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (simLimiarAtingido) {
                            Text(
                                "✓ ${sim.limiarPercentagem}%",
                                color = cores.ok,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (simConcluido && !simLimiarAtingido) {
                            Text(
                                "✗ $simPct%/${sim.limiarPercentagem}%",
                                color = cores.avisoTinta,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (!simConcluido) {
                            TextButton(onClick = { simEmExecucao = !simEmExecucao }) {
                                Text(
                                    if (simEmExecucao) stringResource(R.string.sim_pausar)
                                    else stringResource(R.string.sim_iniciar),
                                    color = cores.azul, fontSize = 11.sp
                                )
                            }
                        }
                        TextButton(onClick = {
                            simEmExecucao = false; simPct = 0
                            simLimiarAtingido = false; simConcluido = false
                            webViewRef?.evaluateJavascript("reiniciaSimulacao();", null)
                        }) {
                            Text(stringResource(R.string.sim_reiniciar), color = cores.suave, fontSize = 11.sp)
                        }
                        // Fechar simulacao
                        IconButton(onClick = {
                            simEmExecucao = false
                            cenarioSimulado = null
                            simPct = 0; simLimiarAtingido = false; simConcluido = false
                            webViewRef?.evaluateJavascript("limpaSim();", null)
                        }, modifier = Modifier.size(28.dp)) {
                            Text("×", color = cores.suave, fontSize = 16.sp)
                        }
                    }
                } else {
                    // Lista de cenarios ativos para simular
                    val cenariosAtivos = cenarios.filter {
                        it.ativo && it.template.size >= 2 &&
                            comandos.any { c -> c.mac == it.macComando }
                    }
                    if (cenariosAtivos.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.sim_escolher),
                                color = cores.suave,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                            cenariosAtivos.forEach { c ->
                                TextButton(onClick = {
                                    cenarioSimulado = c; simPct = 0
                                    simLimiarAtingido = false; simConcluido = false
                                    simEmExecucao = false
                                    // Enviar template ao JS mal a pagina estiver pronta
                                    if (paginaCarregada) {
                                        val json = org.json.JSONArray().apply {
                                            c.template.forEach { p ->
                                                put(org.json.JSONObject().apply {
                                                    put("lat", p.lat); put("lon", p.lon)
                                                })
                                            }
                                        }.toString()
                                        webViewRef?.evaluateJavascript(
                                            "iniciaSimulacao(${org.json.JSONObject.quote(json)}, ${c.raioMetros}, ${c.limiarPercentagem});",
                                            null
                                        )
                                    }
                                }) {
                                    Text("\u25B6 ${c.nome}", color = cores.azul, fontSize = 10.sp)
                                }
                            }
                        }
                    }
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
    onErro: (String) -> Unit,
    onUrlSimulacao: (host: String, url: android.net.Uri) -> Unit = { _, _ -> }
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
                        if (url.scheme == "blugateway-sim") {
                            onUrlSimulacao(url.host ?: "", url)
                            return true
                        }
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
        pontos.sortedBy { it.timestamp }.forEach { p ->
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
