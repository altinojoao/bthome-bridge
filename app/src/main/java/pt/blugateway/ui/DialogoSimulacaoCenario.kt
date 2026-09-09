package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import pt.blugateway.R
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Diálogo de simulação -- reproduz o template do cenario ponto a
 * ponto, calculando a semelhanca incrementalmente (mesmo algoritmo
 * do GestorSemelhancaTrajeto.calculaSemelhanca) e mostrando:
 * - linha verde a crescer no mapa conforme o cursor avanca
 * - percentagem atual no painel sobrepostos no mapa
 * - marcador laranja no ponto onde o limiar seria atingido
 * - botoes de iniciar/pausar/reiniciar
 *
 * Util para confirmar se o template esta correto, qual a
 * percentagem esperada ao final do percurso, e onde a acao
 * dispararia -- sem precisar de fazer o percurso real.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DialogoSimulacaoCenario(
    cenario: CenarioTrajeto,
    onFecha: () -> Unit
) {
    val cores = LocalCoresGateway.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var paginaCarregada by remember { mutableStateOf(false) }
    var templateEnviado by remember { mutableStateOf(false) }
    var emExecucao by remember { mutableStateOf(false) }
    var pct by remember { mutableIntStateOf(0) }
    var limiarAtingido by remember { mutableStateOf(false) }
    var indiceAtual by remember { mutableIntStateOf(0) }
    var concluido by remember { mutableStateOf(false) }

    // Velocidade: ms por ponto de template -- 150ms da uma animacao
    // fluida e rapida o suficiente para ver o trajeto todo em poucos
    // segundos mesmo com 500 pontos OSRM
    val msPorPonto = 150L

    // Enviar o template ao JS logo que a pagina carregue
    LaunchedEffect(paginaCarregada) {
        if (!paginaCarregada || templateEnviado) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        if (cenario.template.isEmpty()) return@LaunchedEffect
        templateEnviado = true

        val jsonTemplate = JSONArray().apply {
            cenario.template.forEach { p ->
                put(JSONObject().apply {
                    put("lat", p.lat)
                    put("lon", p.lon)
                })
            }
        }.toString()

        webView.evaluateJavascript(
            "inicializaSimulacao(${JSONObject.quote(jsonTemplate)}, ${cenario.raioMetros}, ${cenario.limiarPercentagem});",
            null
        )
    }

    // Loop de simulacao -- avanca um passo de cada vez
    LaunchedEffect(emExecucao) {
        while (emExecucao && !concluido) {
            delay(msPorPonto)
            webViewRef?.evaluateJavascript("passaSimulacao();", null)
        }
    }

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.97f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sim_titulo),
                        color = cores.tinta,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.sim_subtitulo, cenario.nome, cenario.template.size, cenario.limiarPercentagem),
                        color = cores.suave,
                        fontSize = 10.sp
                    )
                }
                IconButton(onClick = onFecha) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(9.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    webViewRef = view
                                    paginaCarregada = true
                                }
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    if (url.scheme != "blugateway-sim") return false
                                    when (url.host) {
                                        "progresso" -> {
                                            pct = url.getQueryParameter("pct")?.toIntOrNull() ?: pct
                                            indiceAtual = url.getQueryParameter("i")?.toIntOrNull() ?: indiceAtual
                                        }
                                        "limiar" -> {
                                            limiarAtingido = true
                                        }
                                        "concluido" -> {
                                            emExecucao = false
                                            concluido = true
                                        }
                                    }
                                    return true
                                }
                            }
                            loadUrl("file:///android_asset/leaflet/mapa_simulacao.html")
                        }
                    }
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!concluido) {
                        TextButton(onClick = { emExecucao = !emExecucao }) {
                            Text(
                                if (emExecucao) stringResource(R.string.sim_pausar)
                                else stringResource(R.string.sim_iniciar),
                                color = cores.azul,
                                fontSize = 12.sp
                            )
                        }
                    }
                    TextButton(onClick = {
                        emExecucao = false
                        concluido = false
                        pct = 0
                        indiceAtual = 0
                        limiarAtingido = false
                        webViewRef?.evaluateJavascript("reiniciaSimulacao();", null)
                    }) {
                        Text(stringResource(R.string.sim_reiniciar), color = cores.suave, fontSize = 12.sp)
                    }

                    if (limiarAtingido) {
                        Text(
                            stringResource(R.string.sim_limiar_atingido, cenario.limiarPercentagem),
                            color = cores.ok,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (concluido && !limiarAtingido) {
                        Text(
                            stringResource(R.string.sim_nao_atingiu, pct, cenario.limiarPercentagem),
                            color = cores.avisoTinta,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    )
}
