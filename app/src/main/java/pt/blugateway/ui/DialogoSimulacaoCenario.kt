package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
 * ponto (mesmo algoritmo LCSS), mostrando em tempo real:
 *   - linha azul tracejada: template completo
 *   - linha verde: parte ja "percorrida" (cursor a avançar)
 *   - marcador laranja: onde o limiar seria atingido
 *   - percentagem actual sobreposta no mapa
 *
 * Util para confirmar se o template esta correcto antes de fazer o
 * percurso real, e para diagnosticar porque os cenarios nao disparam.
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

    // Enviar template ao JS assim que a pagina carregue
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

    // Loop de simulacao
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
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = cores.cartao
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabecalho
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sim_titulo),
                            color = cores.tinta,
                            fontSize = 15.sp,
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

                // Mapa -- ocupa a maior parte do espaco disponivel
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp)),
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
                                        }
                                        "limiar" -> { limiarAtingido = true }
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

                // Barra de controlo
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!concluido) {
                        TextButton(onClick = { emExecucao = !emExecucao }) {
                            Text(
                                if (emExecucao) stringResource(R.string.sim_pausar)
                                else stringResource(R.string.sim_iniciar),
                                color = cores.azul,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    TextButton(onClick = {
                        emExecucao = false
                        concluido = false
                        pct = 0
                        limiarAtingido = false
                        webViewRef?.evaluateJavascript("reiniciaSimulacao();", null)
                    }) {
                        Text(stringResource(R.string.sim_reiniciar), color = cores.suave, fontSize = 12.sp)
                    }

                    Spacer(Modifier.weight(1f))

                    // Estado: percentagem ou resultado final
                    if (limiarAtingido) {
                        Text(
                            stringResource(R.string.sim_limiar_atingido, cenario.limiarPercentagem),
                            color = cores.ok,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (concluido) {
                        Text(
                            stringResource(R.string.sim_nao_atingiu, pct, cenario.limiarPercentagem),
                            color = cores.avisoTinta,
                            fontSize = 12.sp
                        )
                    } else if (pct > 0) {
                        Text(
                            "$pct%",
                            color = cores.tinta,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
