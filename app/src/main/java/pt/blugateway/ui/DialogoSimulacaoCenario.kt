package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import pt.blugateway.R
import pt.blugateway.data.CenarioTrajeto
import pt.blugateway.ui.theme.LocalCoresGateway

/**
 * Simulador de cenario de trajeto -- reproduz o template ponto a
 * ponto (mesmo algoritmo LCSS), mostrando em tempo real no mapa:
 *   - linha azul tracejada: template completo
 *   - linha verde: percurso ja simulado
 *   - marcador laranja: ponto onde o limiar seria atingido
 *   - percentagem actual no painel HTML sobrepostos no mapa
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
    var concluido by remember { mutableStateOf(false) }

    LaunchedEffect(paginaCarregada) {
        if (!paginaCarregada || templateEnviado) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        if (cenario.template.isEmpty()) return@LaunchedEffect
        templateEnviado = true
        val json = JSONArray().apply {
            cenario.template.forEach { p ->
                put(JSONObject().apply { put("lat", p.lat); put("lon", p.lon) })
            }
        }.toString()
        wv.evaluateJavascript(
            "inicializaSimulacao(${JSONObject.quote(json)}, ${cenario.raioMetros}, ${cenario.limiarPercentagem});",
            null
        )
    }

    LaunchedEffect(emExecucao) {
        while (emExecucao && !concluido) {
            delay(150L)
            webViewRef?.evaluateJavascript("passaSimulacao();", null)
        }
    }

    Dialog(
        onDismissRequest = onFecha,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = cores.cartao
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {

                // Cabecalho
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sim_titulo),
                            color = cores.tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.sim_subtitulo, cenario.nome, cenario.template.size, cenario.limiarPercentagem),
                            color = cores.suave, fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = {
                        emExecucao = false
                        onFecha()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fechar), tint = cores.suave)
                    }
                }

                // Mapa -- MATCH_PARENT no WebView garante que preenche
                // o seu container nativo, independentemente de como o
                // Compose propaga as restricoes de tamanho
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url ?: return false
                                    if (url.scheme != "blugateway-sim") return false
                                    when (url.host) {
                                        "progresso" -> pct = url.getQueryParameter("pct")?.toIntOrNull() ?: pct
                                        "limiar" -> limiarAtingido = true
                                        "concluido" -> { emExecucao = false; concluido = true }
                                    }
                                    return true
                                }
                            }
                            loadUrl("file:///android_asset/leaflet/mapa_simulacao.html")
                        }
                    }
                )

                // Controlos
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!concluido) {
                        TextButton(onClick = { emExecucao = !emExecucao }) {
                            Text(
                                if (emExecucao) stringResource(R.string.sim_pausar)
                                else stringResource(R.string.sim_iniciar),
                                color = cores.azul, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    TextButton(onClick = {
                        emExecucao = false; concluido = false; pct = 0; limiarAtingido = false
                        webViewRef?.evaluateJavascript("reiniciaSimulacao();", null)
                    }) {
                        Text(stringResource(R.string.sim_reiniciar), color = cores.suave, fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    when {
                        limiarAtingido -> Text(
                            stringResource(R.string.sim_limiar_atingido, cenario.limiarPercentagem),
                            color = cores.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        concluido -> Text(
                            stringResource(R.string.sim_nao_atingiu, pct, cenario.limiarPercentagem),
                            color = cores.avisoTinta, fontSize = 12.sp
                        )
                        pct > 0 -> Text(
                            "$pct%", color = cores.tinta, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
