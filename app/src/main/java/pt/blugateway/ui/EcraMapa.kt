package pt.blugateway.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
   Usa um WebView com Leaflet.js e tiles OpenStreetMap -- os ficheiros
   do Leaflet (JS/CSS/imagens) estao empacotados em assets/leaflet/,
   nao dependem de rede so para ABRIR o mapa (os tiles em si, sim,
   precisam de rede para carregar as imagens do mapa-base).

   E um retrato do trajeto no momento em que o ecra abre -- nao se
   atualiza sozinho enquanto fica aberto, mesmo que chegue um ponto
   novo de beacon nesse intervalo (historicoTrajeto() le direto de
   SharedPreferences, nao e um StateFlow observavel).

   Os pontos sao enviados para dentro da pagina via
   evaluateJavascript() depois da pagina ja estar carregada
   (onPageFinished), nunca embutidos no HTML inicial -- evita ter de
   escapar JSON dinamico dentro de uma string HTML. */
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

    // le o historico de cada comando UMA vez por composicao, tanto
    // para desenhar o mapa como para a lista de "limpar trajeto"
    val comandosComHistorico = remember(comandos) {
        comandos.mapNotNull { c ->
            val pontos = vm.historicoTrajeto(c.mac)
            if (pontos.isEmpty()) null else c to pontos
        }
    }

    val jsonTrajetos = remember(comandosComHistorico) {
        construirJsonTrajetos(comandosComHistorico)
    }

    LaunchedEffect(jsonTrajetos, paginaCarregada) {
        if (paginaCarregada) {
            val script = "desenhaTrajetos(${JSONObject.quote(jsonTrajetos)});"
            webViewRef?.evaluateJavascript(script, null)
        }
    }

    AlertDialog(
        onDismissRequest = onFecha,
        confirmButton = {},
        containerColor = cores.cartao,
        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
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
        },
        text = {
            Column(Modifier.fillMaxSize()) {
                MapaWebView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onPaginaCarregada = { webView ->
                        webViewRef = webView
                        paginaCarregada = true
                    }
                )

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
    )

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
private fun MapaWebView(modifier: Modifier = Modifier, onPaginaCarregada: (WebView) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                // sem acesso a rede alem dos tiles do proprio mapa (que
                // usam http/https normal, nao file://) -- nao precisa de
                // allowFileAccess nem de outras permissoes de WebView
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        onPaginaCarregada(view)
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
            })
        }
        obj.put("pontos", arrPontos)
        arr.put(obj)
    }
    return arr.toString()
}
